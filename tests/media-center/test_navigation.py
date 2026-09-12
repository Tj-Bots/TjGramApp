"""Source wiring checks, not Android lifecycle/gesture tests."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2] / "TMessagesProj/src/main/java/org/telegram"
config = (root / "messenger/tj/TjConfig.java").read_text()
tabs = (root / "ui/MainTabsActivity.java").read_text()
drawer = (root / "ui/Adapters/DrawerLayoutAdapter.java").read_text()
launch = (root / "ui/LaunchActivity.java").read_text()
settings = (root / "ui/TjPrivacySettingsActivity.java").read_text()
media = (root / "ui/TjMediaCenterActivity.java").read_text()

assert 'get("show_media_in_drawer", true)' in config
assert 'get("show_media_tab", false)' in config
assert re.search(r'if \(TjConfig.showMediaInDrawer\(\)\) \{\s*items.add\(new Item\(106,', drawer)
route = launch.split("case 106:", 1)[1].split("break;", 1)[0]
assert route.index("setCurrentAccount(currentAccount)") < route.index("presentFragment(mediaCenter)")
for key in ["SHOW_MEDIA_IN_DRAWER", "SHOW_MEDIA_TAB"]:
    assert f"TYPE_CHECK, {key}" in settings
    assert f"case {key}: return TjConfig." in settings
    assert f"case {key}: key =" in settings
assert "NotificationCenter.tjMediaNavigationChanged" in settings
assert ".add(NotificationCenter.tjMediaNavigationChanged)" in tabs
assert "TABS_COUNT + (mediaTabVisible ? 1 : 0)" in tabs
expected = {"CHATS": 0, "CONTACTS": 1, "CALLS_OR_SETTINGS": 2, "PROFILE": 3, "MEDIA": 4}
for name, value in expected.items():
    assert re.search(rf"POSITION_{name}\s*=\s*{value};", tabs), name
assert "new GlassTabView[6]" in tabs
assert "INDEX_MEDIA = 5" in tabs
assert "return index > 2 ? index - 1 : index;" in tabs
factory = tabs.split("if (position == POSITION_MEDIA)", 1)[1].split("return media;", 1)[0]
assert "setCurrentAccount(currentAccount)" in factory and "setMainTabBackAction" in factory
toggle = tabs.split("private void checkUi_mediaTabVisible()", 1)[1].split("private void checkUi_callTabVisible", 1)[0]
assert toggle.index("viewPager.setPosition(POSITION_CHATS)") < toggle.index("mediaTabVisible = enabled")
assert "dropFragmentAtPosition(POSITION_MEDIA)" in toggle and "viewPager.rebuild(false)" in toggle
assert "mainTabBackAction.run()" in media
assert "tabsViewWrapper.setVisibility(position == POSITION_MEDIA ? View.GONE : View.VISIBLE)" in tabs
assert media.index("root.addView(mainTabs,") > media.index("root.addView(pages,")
assert "new MainTabsLayout(context, getResourceProvider())" in media
assert "GlassTabView.createMainTab" in media
assert "DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS" in media
assert "DialogsActivity.MAIN_TABS_MARGIN + 4" in media
assert "BlurredBackgroundProviderImpl.mainTabs(getResourceProvider())" in media
assert "newItemsButton" not in media and "updateNewItemsButton" not in media
assert "automatic ? displayedBoundaries" in media
assert "automatic ? displayedCatalogBoundary" in media
assert "layout.scrollToPositionWithOffset(i, anchorOffset)" in media
automatic = media.split("private void applyAutomaticRefresh()", 1)[1].split("private void onScanChanged()", 1)[0]
assert "reload()" not in automatic and "interactionInProgress()" in automatic
scan_update = media.split("private void onScanChanged()", 1)[1].split("private TextView label", 1)[0]
assert "pendingStoreUpdate = true" not in scan_update
assert "tabletLayout || viewPager.getCurrentPosition() == POSITION_MEDIA" in tabs
reload = media.split("private void reload()", 1)[1].split("private CharSequence[] viewNames()", 1)[0]
assert "dismissCurrentDialog()" not in reload
store_changed = media.split("storeChanged =", 1)[1].split(";", 1)[0]
assert "reload" not in store_changed
destroy = media.split("void onFragmentDestroy()", 1)[1].split("@Override", 1)[0]
assert "stopScan()" not in destroy
assert "TjMediaScanCoordinator.getInstance().setForeground(false)" in launch
assert "TjMediaScanCoordinator.getInstance().setForeground(true)" in launch
print("Navigation defaults, stable slots, account routing and toggle wiring checks passed")
