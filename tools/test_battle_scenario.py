from pathlib import Path
import tempfile
import unittest

from battle_scenario import battle_model_errors


class BattlePackLoadTest(unittest.TestCase):
    def test_rejected_battle_model_and_metadata_fail(self):
        rejected = [
            '[Worker/ERROR] Failed to load model solaris_battle:models/item/iron_lance.json',
            '[Worker/WARN] Missing block model: solaris_battle:item/iron_halberd',
            '[Render thread/WARN] Error reading pack metadata, attempting fallback type',
        ]
        with tempfile.TemporaryDirectory() as root:
            log = Path(root) / 'client.log'
            log.write_text('\n'.join(rejected + [
                'Missing model for variant: Block{solaris_loader:loader_block}',
                'Reloading ResourceManager: vanilla',
            ]))
            self.assertEqual(battle_model_errors([log]), {str(log): rejected})

    def test_valid_pack_load_has_no_errors(self):
        with tempfile.TemporaryDirectory() as root:
            log = Path(root) / 'client.log'
            log.write_text('Reloading ResourceManager: vanilla, server/123\n')
            self.assertEqual(battle_model_errors([log]), {str(log): []})


if __name__ == '__main__':
    unittest.main()
