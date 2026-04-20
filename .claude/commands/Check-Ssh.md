# /check-ssh

Verify SSH connectivity to VPS.

## Steps
1. Test SSH connection: `ssh vps echo "Connected"`
2. On Windows: use `/c/Windows/System32/OpenSSH/ssh.exe` (not Git Bash ssh)
3. If fails: check SSH key, check alias in ~/.ssh/config
4. Report: connected or error details
