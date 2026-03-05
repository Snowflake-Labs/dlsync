# Contributing to DLSync

Thank you for your interest in contributing to DLSync! We welcome contributions from the community.

## Prerequisites

- Java 11 or higher
- Gradle 7.0 or higher
- Git
- Snowflake account (for testing changes)

## Quick Start

### 1. Fork the Repository

1. Click **Fork** on the [DLSync GitHub repository](https://github.com/Snowflake-Labs/dlsync)
2. This creates a copy under your GitHub account

### 2. Clone Your Fork

```bash
git clone https://github.com/YOUR_USERNAME/dlsync.git
cd dlsync
```

### 3. Add Upstream Remote

```bash
git remote add upstream https://github.com/Snowflake-Labs/dlsync.git
```

---

## Making Changes

### 1. Create a Feature Branch

```bash
# Update your local main branch
git fetch upstream
git checkout main
git rebase upstream/main

# Create a new branch for your changes
git checkout -b feature/your-feature-name
```

### 2. Make Your Changes

- Edit the necessary files
- Keep changes focused and concise
- Follow the existing code style

### 3. Test Your Changes

```bash
# Run tests
./gradlew test

# Build the project
./gradlew build
```

### 4. Update Documentation

- Update `README.md` if user-facing changes
- add changelog to `CHANGELOG.md`

### 5. Commit Your Changes

```bash
git add .
git commit -m "Description of your changes"
```

---

## Creating a Pull Request

### 1. Push Your Branch

```bash
git push origin feature/your-feature-name
```

### 2. Create Pull Request on GitHub

1. Go to your forked repository on GitHub: `https://github.com/YOUR_USERNAME/dlsync`
2. You should see a notification: "Your branch is ahead of Snowflake-Labs:main"
3. Click the **Compare & pull request** button (or go to **Pull Requests** tab → **New Pull Request**)
4. Verify the pull request details:
   - **Base repository**: `Snowflake-Labs/dlsync`
   - **Base branch**: `main`
   - **Head repository**: `YOUR_USERNAME/dlsync`
   - **Compare branch**: `feature/your-feature-name`
5. Add a title and description:
   ```
   Title: [FEATURE|FIX|DOCS] Brief description of changes
   
   Description:
   - What changes are included
   - Why this change is needed
   - Any related issues (e.g., Closes #123)
   ```
6. Click **Create Pull Request**

### 4. Review Process

- A maintainer will review your PR
- CI/CD checks must pass
- Address any feedback or changes requested
- Once approved, your PR will be merged!

---

Thank you for contributing! 🙏

