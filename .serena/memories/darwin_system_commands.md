# macOS (Darwin) System Commands for A2A Java SDK

## System Information
```bash
# System version
uname -a              # Kernel and OS info
sw_vers               # macOS version details

# Java environment
java -version         # Java runtime version
mvn -version         # Maven version
echo $JAVA_HOME      # Java installation path
```

## File Operations
```bash
# Navigation
ls -la               # List all files with details
cd <directory>       # Change directory
pwd                  # Print working directory
pushd <dir>          # Save and change directory
popd                 # Return to saved directory

# File manipulation
cp -r <src> <dst>    # Copy recursively
mv <src> <dst>       # Move/rename
rm -rf <path>        # Remove recursively (dangerous!)
mkdir -p <path>      # Create directory with parents
touch <file>         # Create empty file
```

## Search & Find
```bash
# Find files
find . -name "*.java"              # Find Java files
find . -type f -name "pom.xml"     # Find all pom.xml files
mdfind "kind:folder name:a2a"      # Spotlight search for folders

# Search content
grep -r "pattern" --include="*.java" .     # Search in Java files
grep -rn "AgentExecutor" .                 # Search with line numbers
egrep -r "interface|class" <path>          # Extended regex search

# Advanced find
find . -name "*.java" -exec grep -l "AgentCard" {} \;  # Files containing pattern
```

## Process Management
```bash
# View processes
ps aux | grep java               # Find Java processes
pgrep -fl java                   # Process grep for Java
top -o cpu                       # Top processes by CPU

# Kill processes
kill <pid>                       # Graceful kill
kill -9 <pid>                    # Force kill
killall java                     # Kill all Java processes
pkill -f "quarkus:dev"          # Kill by command pattern
```

## Network & Port Management
```bash
# Check ports
lsof -i :8080                    # What's using port 8080
netstat -an | grep LISTEN        # All listening ports
sudo lsof -iTCP -sTCP:LISTEN -n -P  # All listening TCP ports

# Kill process on port
lsof -ti:8080 | xargs kill -9    # Kill whatever's on port 8080
```

## File Permissions
```bash
# View permissions
ls -l <file>                     # Long format with permissions
stat <file>                      # Detailed file info

# Change permissions
chmod +x <file>                  # Make executable
chmod 644 <file>                 # rw-r--r--
chmod -R 755 <directory>         # Recursive permissions
chown <user>:<group> <file>      # Change ownership
```

## Environment & Path
```bash
# View environment
env                              # All environment variables
echo $PATH                       # View PATH
echo $JAVA_HOME                  # View JAVA_HOME

# Modify for session
export JAVA_HOME=/path/to/java   # Set Java home
export PATH=$PATH:/new/path      # Append to PATH

# Persistent changes
# Edit ~/.zshrc or ~/.bash_profile depending on shell
echo $SHELL                      # Check current shell
```

## File Content Viewing
```bash
# View files
cat <file>                       # Display entire file
less <file>                      # Page through file
head -n 20 <file>               # First 20 lines
tail -n 20 <file>               # Last 20 lines
tail -f <file>                  # Follow file (for logs)

# Multiple files
cat file1 file2 > combined       # Concatenate files
```

## Compression & Archives
```bash
# Create archives
tar -czf archive.tar.gz <dir>    # Create compressed tarball
zip -r archive.zip <dir>         # Create zip archive

# Extract archives
tar -xzf archive.tar.gz          # Extract tarball
unzip archive.zip                # Extract zip
jar -xf file.jar                 # Extract JAR file
```

## Disk Usage
```bash
# Check space
df -h                            # Disk free space
du -sh <directory>               # Directory size
du -h --max-depth=1              # Size of subdirectories

# Find large files
find . -type f -size +100M       # Files larger than 100MB
du -a . | sort -n -r | head -20  # 20 largest files
```

## macOS-Specific
```bash
# Open commands
open .                           # Open current dir in Finder
open <file>                      # Open with default app
open -a "IntelliJ IDEA" .       # Open with specific app

# System info
system_profiler SPSoftwareDataType    # Software info
sysctl -n machdep.cpu.brand_string    # CPU info

# Clipboard
pbcopy < file.txt                # Copy file to clipboard
pbpaste > file.txt               # Paste from clipboard

# Network
networksetup -listallhardwareports   # List network interfaces
```

## Git Operations (Darwin)
```bash
# Status & info
git status                       # Working tree status
git branch                       # List branches
git log --oneline -10           # Recent commits

# Remote operations
git remote -v                    # View remotes
git fetch upstream               # Fetch from upstream
git pull origin main             # Pull from origin

# Stash operations
git stash                        # Stash changes
git stash list                   # List stashes
git stash pop                    # Apply and remove stash
```

## Maven on macOS
```bash
# Maven home
echo $M2_HOME                    # Maven installation
ls ~/.m2/repository             # Local repository

# Clear Maven cache
rm -rf ~/.m2/repository         # Delete local repo (careful!)

# Settings
cat ~/.m2/settings.xml          # View Maven settings
```

## Useful Aliases (add to ~/.zshrc or ~/.bash_profile)
```bash
alias ll='ls -laGh'              # Better ls
alias grep='grep --color=auto'   # Colorized grep
alias ..='cd ..'                 # Quick parent directory
alias ...='cd ../..'             # Two levels up
alias gs='git status'            # Quick git status
alias gd='git diff'              # Quick git diff
alias mvnci='mvn clean install'  # Maven shortcut
alias mvncs='mvn clean install -DskipTests'  # Skip tests
```

## Performance Monitoring
```bash
# Activity monitor (GUI)
open -a "Activity Monitor"       # Open Activity Monitor app

# Command line monitoring
top -l 1                         # One snapshot of top
iostat -w 1                      # I/O statistics
vm_stat 1                        # Virtual memory stats
```

## File Watching (for development)
```bash
# Watch file changes
fswatch -o <path> | xargs -n1 -I{} <command>  # Execute on change

# Example: run tests on change
fswatch -o src/ | xargs -n1 -I{} mvn test
```

## Networking & Testing
```bash
# HTTP requests
curl http://localhost:9999/a2a/agent-card     # GET request
curl -X POST -H "Content-Type: application/json" \
     -d '{"key":"value"}' http://localhost:9999  # POST request

# Port forwarding
ssh -L 8080:localhost:8080 user@remote         # Forward port

# DNS
nslookup example.com             # DNS lookup
dig example.com                  # Detailed DNS info
```

## Differences from Linux
- Use `open` instead of `xdg-open`
- Use `pbcopy`/`pbpaste` instead of `xclip`
- GNU utils may differ (install with `brew install coreutils` for GNU versions)
- Case-insensitive filesystem by default (APFS can be case-sensitive)
- Different package manager: Homebrew (`brew`) instead of apt/yum
