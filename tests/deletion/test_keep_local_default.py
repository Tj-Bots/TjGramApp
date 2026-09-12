"""Deletion dialog source contract; does not execute Android UI or delete messages."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
source = (root / 'TMessagesProj/src/main/java/org/telegram/ui/Components/AlertsCreator.java').read_text()
choice = source.split('final boolean[] keepLocally = {false};', 1)[1].split(
    'AlertDialog.OnButtonClickListener deleteAction', 1)[0]
assert 'keepLocally[0] = true' not in choice
assert 'keepCell.setChecked(true' not in choice
assert 'keepCell.setChecked(keepLocally[0], false)' in choice
assert 'keepLocally[0] = !keepLocally[0]' in choice
assert 'keepCell.setChecked(keepLocally[0], true)' in choice
action = source.split('AlertDialog.OnButtonClickListener deleteAction', 1)[1].split('if (onDelete != null)', 1)[0]
assert action.count('if (!keepLocally[0])') == 2
assert action.count('TjDeletionPolicy.markLocalRemoval(') == 2
assert action.count('TjMessageArchive.getInstance().deleteSnapshots(') == 2
print('Keep-local default: unchecked, manual opt-in, single and bulk deletion guards passed')
