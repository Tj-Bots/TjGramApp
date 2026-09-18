package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FlagSecureReason;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.messenger.tj.TjMessageArchive;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import java.io.File;
import java.util.ArrayList;

/** Read-only owner-scoped archive bubbles, never a live ChatActivity. */
public class TjMessageHistoryActivity extends BaseFragment {
    private final MessageObject currentMessage;
    private final long ownerId;
    private final ArrayList<Revision> revisions = new ArrayList<>();
    private RecyclerView listView;
    private TextView emptyView;
    private FlagSecureReason secureReason;
    private boolean destroyed;
    private int generation;

    static final class Revision {
        MessageObject message;
        String localPath;
        long date;
        int number;
    }

    public TjMessageHistoryActivity(MessageObject currentMessage) {
        this.currentMessage = currentMessage;
        setCurrentAccount(currentMessage.currentAccount);
        ownerId = getUserConfig().getClientUserId();
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(TjLocale.getString(R.string.TjEditHistory));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        SizeNotifierFrameLayout root = new SizeNotifierFrameLayout(context) {
            /**
             * In a chat the wallpaper is drawn starting below the action bar, because the messages
             * start there too. Here the list runs all the way to the top of the screen, so pushing
             * the wallpaper down by an action bar left a band under the title with nothing but the
             * plain wallpaper colour in it - red in one theme and something else in the next. The
             * picture is drawn from the top instead, and the action bar sits over it.
             */
            @Override protected boolean isActionBarVisible() {
                return false;
            }
        };
        root.setOccupyStatusBar(false);
        root.setBackgroundImage(Theme.getCachedWallpaperNonBlocking(), false);
        root.setBackgroundColor(Theme.getColor(Theme.key_chat_wallpaper));
        fragmentView = root;
        listView = new RecyclerView(context);
        LinearLayoutManager manager = new LinearLayoutManager(context);
        manager.setStackFromEnd(true);
        listView.setLayoutManager(manager);
        listView.setItemAnimator(null);
        listView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        listView.setClipToPadding(false);
        listView.setAdapter(new HistoryAdapter());
        root.addView(listView, LayoutHelper.createFrame(-1, -1));
        emptyView = new TextView(context);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(15);
        emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        emptyView.setText(LocaleController.getString(R.string.Loading));
        emptyView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        emptyView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_chat_serviceBackground)));
        root.addView(emptyView, LayoutHelper.createFrame(-2, -2, Gravity.CENTER, 24, 0, 24, 0));
        if (getParentActivity() != null) {
            secureReason = new FlagSecureReason(getParentActivity().getWindow(), () ->
                    DialogObject.isEncryptedDialog(currentMessage.getDialogId())
                    || currentMessage.messageOwner.media != null && currentMessage.messageOwner.media.ttl_seconds != 0
                    || getMessagesController().isChatNoForwards(-currentMessage.getDialogId()) && !TjConfig.allowProtectedScreenshots());
        }
        loadHistory();
        return root;
    }

    private boolean valid(int request) {
        return !destroyed && request == generation && ownerId == getUserConfig().getClientUserId();
    }

    private void loadHistory() {
        int request = ++generation;
        TjMessageArchive.getInstance().getRevisions(currentAccount, currentMessage.getDialogId(), currentMessage.getId(), snapshots -> {
            if (!valid(request)) return;
            // File checks are off the UI thread and only resolve each version's own attachment.
            Utilities.globalQueue.postRunnable(() -> {
                ArrayList<Revision> loaded = new ArrayList<>();
                for (TjMessageArchive.Snapshot snapshot : snapshots) {
                    if (snapshot.message == null || snapshot.ownerUserId != ownerId || snapshot.accountId != currentAccount) continue;
                    Revision revision = new Revision();
                    revision.number = loaded.size() + 1;
                    revision.date = (snapshot.editDate > 0 ? snapshot.editDate : snapshot.message.date) * 1000L;
                    revision.localPath = existingFile(snapshot.message.attachPath);
                    if (revision.localPath == null) {
                        File file = FileLoader.getInstance(currentAccount).getPathToMessage(snapshot.message);
                        if (file != null && file.isFile()) revision.localPath = file.getAbsolutePath();
                    }
                    // These are deserialized archive copies, not the live message.
                    snapshot.message.date = (int) (revision.date / 1000L);
                    snapshot.message.edit_date = 0;
                    snapshot.message.flags &= ~32768;
                    revision.message = new MessageObject(currentAccount, snapshot.message, true, true);
                    if (revision.localPath != null) {
                        revision.message.messageOwner.attachPath = revision.localPath;
                        revision.message.attachPathExists = true;
                        revision.message.mediaExists = true;
                    }
                    loaded.add(revision);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (!valid(request) || listView == null) return;
                    revisions.clear();
                    revisions.addAll(loaded);
                    listView.getAdapter().notifyDataSetChanged();
                    emptyView.setText(TjLocale.getString(R.string.TjNoEditHistory));
                    emptyView.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE);
                });
            });
        });
    }

    private static String existingFile(String path) {
        return !TextUtils.isEmpty(path) && new File(path).isFile() ? path : null;
    }

    @Override public void onResume() {
        super.onResume();
        if (secureReason != null) secureReason.attach();
    }

    @Override public void onPause() {
        super.onPause();
        if (secureReason != null) secureReason.detach();
    }

    @Override public void onFragmentDestroy() {
        destroyed = true;
        generation++;
        if (secureReason != null) secureReason.detach();
        super.onFragmentDestroy();
    }

    private class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return revisions.size(); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            RevisionCell cell = new RevisionCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(cell) {};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((RevisionCell) holder.itemView).bind(revisions.get(position));
        }
    }

    private class RevisionCell extends LinearLayout {
        private final ChatActionCell date;
        private final ChatMessageCell bubble;
        private final TextView notice;
        private Revision revision;

        RevisionCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            date = new ChatActionCell(context);
            addView(date, LayoutHelper.createLinear(-1, -2));
            bubble = new ChatMessageCell(context, currentAccount, false, null, null) {
                @Override protected void onMeasure(int width, int height) {
                    super.onMeasure(width, height);
                    if (revision != null && revision.localPath != null && revision.message.isPhoto()
                            && !revision.message.needDrawBluredPreview() && !revision.message.hasMediaSpoilers()) {
                        getPhotoImage().setImage(ImageLocation.getForPath(revision.localPath), "800_800",
                                null, null, null, 0, null, revision.message, 1);
                    }
                }
            };
            bubble.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {});
            bubble.setFullyDraw(true);
            addView(bubble, LayoutHelper.createLinear(-1, -2));
            notice = new TextView(context);
            notice.setText(TjLocale.getString(R.string.TjHistoryMediaNotLocal));
            notice.setTextSize(12);
            notice.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
            notice.setGravity(Gravity.CENTER);
            notice.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
            notice.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_chat_serviceBackground)));
            addView(notice, LayoutHelper.createLinear(-1, -2, 16, 4, 16, 8));
        }

        void bind(Revision value) {
            revision = value;
            date.setCustomText(TjLocale.formatString(R.string.TjEditRevision, value.number) + " · "
                    + LocaleController.getInstance().getFormatterStats().format(value.date));
            bubble.forceResetMessageObject();
            bubble.setMessageObject(value.message, null, false, false, false);
            boolean attachment = value.message.isPhoto() || value.message.getDocument() != null;
            notice.setVisibility(attachment && value.localPath == null ? VISIBLE : GONE);
        }
    }
}
