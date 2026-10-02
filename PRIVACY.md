# Public-data policy

Publish only source, reviewed modpack files and public verification keys. Keep SSH keys, signing private keys, tokens, account databases, launcher account/profile backups, diagnostic logs and computer-specific paths out of GitHub.

The game connection address and this public GitHub account are intentionally public so friends can download updates and join. Administration examples use placeholders. Set the real SSH host/user and key path locally.

Before publishing, run `python scripts/check_public_data.py`. To check a prepared archive, run `python scripts/check_public_data.py path/to/archive.zip`. The check also examines nested archives and prints filenames without printing matching secret values. This is a bounded check, not a guarantee against every possible disclosure.

GitHub secret scanning and push protection remain enabled. Public release builds omit debug symbols to avoid embedding local build paths. Review new configs and images before sharing. Never upload the entire game or server directory.

Existing downloads, clones, forks and GitHub caches cannot be erased by editing the repository. If a credential was published, revoke it immediately and contact GitHub Support if additional cache removal is needed.
