# 🔐 Security Setup Guide for GitHub Actions

## ⚠️ IMPORTANT: Protecting Sensitive Information

Since your repository is **public**, you **MUST** use GitHub Secrets to protect sensitive credentials. Never commit these files directly to the repository.

---

## 🚨 Files That Must NOT Be Committed

The following files are automatically excluded via `.gitignore`:

- ✅ `client_secret_*.json` - Google OAuth credentials
- ✅ `google-services.json` - Firebase/Google Services config
- ✅ `*.keystore`, `*.jks` - Android signing keys
- ✅ `secrets.properties` - Any properties files with secrets
- ✅ `.env` files - Environment variables

---

## 🔑 Setting Up GitHub Secrets

### Step 1: Access Repository Settings

1. Go to your repository: https://github.com/rishikeshmore18/Foto-Nizer
2. Click **Settings** (top menu)
3. Click **Secrets and variables** → **Actions** (left sidebar)
4. Click **New repository secret**

### Step 2: Add Required Secrets

#### 1. Google Services JSON
- **Name**: `GOOGLE_SERVICES_JSON`
- **Value**: Copy the entire contents of `app/google-services.json`
- **How to get**: Open `app/google-services.json` locally and copy all content

#### 2. Client Secret JSON
- **Name**: `CLIENT_SECRET_JSON`
- **Value**: Copy the entire contents of `app/client_secret_*.json`
- **How to get**: Open your `client_secret_*.json` file locally and copy all content

#### 3. (Optional) Android Signing Keys (for release builds)
If you want to build signed release APKs/AABs:

- **Name**: `KEYSTORE_FILE`
- **Value**: Base64-encoded keystore file content
  ```bash
  # On local machine:
  base64 -i your-keystore.jks
  ```

- **Name**: `KEYSTORE_PASSWORD`
- **Value**: Your keystore password

- **Name**: `KEY_ALIAS`
- **Value**: Your key alias

- **Name**: `KEY_PASSWORD`
- **Value**: Your key password

---

## 📋 Quick Setup Checklist

- [ ] Verify `.gitignore` includes all sensitive files
- [ ] Add `GOOGLE_SERVICES_JSON` secret to GitHub
- [ ] Add `CLIENT_SECRET_JSON` secret to GitHub
- [ ] (Optional) Add signing key secrets if building release
- [ ] Verify sensitive files are NOT in git:
  ```bash
  git status
  git ls-files | grep -E "(client_secret|google-services|\.keystore|\.jks)"
  ```
- [ ] Test workflow runs successfully

---

## 🔍 Verifying Secrets Are Protected

### Check if sensitive files are tracked:
```bash
# This should return NOTHING if files are properly ignored
git ls-files | grep -E "(client_secret|google-services)"
```

### Check .gitignore is working:
```bash
# Try to add a test file (should be ignored)
echo "test" > app/test_client_secret.json
git status  # Should NOT show test_client_secret.json
rm app/test_client_secret.json
```

---

## 🛡️ Additional Security Best Practices

### 1. Use Environment-Specific Secrets
- Development secrets (if needed)
- Production secrets (separate)

### 2. Rotate Secrets Regularly
- Update OAuth credentials periodically
- Regenerate keys if compromised

### 3. Limit Secret Access
- Only add secrets that are absolutely necessary
- Use least-privilege principle

### 4. Monitor Workflow Logs
- Review GitHub Actions logs for exposed secrets
- GitHub automatically masks secrets in logs

### 5. Use Secret Scanning
- Enable GitHub's secret scanning alerts
- Repository → Settings → Security → Code security and analysis

---

## 🚨 If You Accidentally Committed Secrets

### Immediate Actions:

1. **Remove from Git History:**
   ```bash
   # Remove file from git but keep locally
   git rm --cached app/client_secret*.json
   git rm --cached app/google-services.json
   
   # Commit the removal
   git commit -m "security: remove sensitive files from repository"
   git push origin main
   ```

2. **Rotate Credentials:**
   - Generate new OAuth client secrets in Google Cloud Console
   - Update `google-services.json` if needed
   - Update GitHub Secrets with new values

3. **Clean Git History (if needed):**
   ```bash
   # Use git filter-branch or BFG Repo-Cleaner
   # WARNING: This rewrites history - coordinate with team
   ```

---

## 📚 Resources

- [GitHub Secrets Documentation](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [GitHub Secret Scanning](https://docs.github.com/en/code-security/secret-scanning)
- [Android Signing Guide](https://developer.android.com/studio/publish/app-signing)

---

## ✅ Verification Commands

```bash
# 1. Check sensitive files are ignored
git check-ignore app/client_secret*.json app/google-services.json

# 2. Verify files are NOT tracked
git ls-files | grep -E "(client_secret|google-services)"

# 3. Check .gitignore patterns
cat .gitignore | grep -E "(client_secret|google-services)"
```

---

**Last Updated**: 2025
**Repository**: Foto-Nizer
**Status**: Public Repository - Secrets Required

