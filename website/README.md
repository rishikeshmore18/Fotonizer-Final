# Foto-Nizer Website

This is the official website for the Foto-Nizer photo and video management app.

## Files

- `index.html` - Main landing page
- `terms.html` - Terms and Conditions
- `privacy.html` - Privacy Policy
- `styles.css` - Stylesheet for all pages
- `README.md` - This file

## Hosting on GitHub Pages

### Step 1: Create a GitHub Repository

1. Go to [GitHub](https://github.com) and sign in
2. Click the "+" icon in the top right corner
3. Select "New repository"
4. Name it `foto-nizer-website` (or any name you prefer)
5. Make it **Public** (required for free GitHub Pages)
6. Click "Create repository"

### Step 2: Upload Files

**Option A: Using GitHub Web Interface**

1. In your new repository, click "Add file" → "Upload files"
2. Drag and drop all files from the `website` folder:
   - `index.html`
   - `terms.html`
   - `privacy.html`
   - `styles.css`
3. Click "Commit changes"

**Option B: Using Git Command Line**

```bash
cd website
git init
git add .
git commit -m "Initial commit: Foto-Nizer website"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/foto-nizer-website.git
git push -u origin main
```

### Step 3: Enable GitHub Pages

1. Go to your repository on GitHub
2. Click "Settings" (top menu)
3. Scroll down to "Pages" in the left sidebar
4. Under "Source", select "Deploy from a branch"
5. Select "main" branch and "/ (root)" folder
6. Click "Save"

### Step 4: Access Your Website

Your website will be available at:
- `https://YOUR_USERNAME.github.io/foto-nizer-website/`

It may take a few minutes for the site to be live. GitHub will show you the URL once it's ready.

## Using a Custom Domain (Optional)

### Step 1: Register a Domain

Register a domain from:
- Namecheap (~$8-12/year)
- Google Domains (~$12/year)
- Cloudflare Registrar (~$8-10/year)

### Step 2: Add CNAME File

1. Create a file named `CNAME` (no extension) in your repository
2. Add your domain name (e.g., `foto-nizer.com`)
3. Commit and push the file

### Step 3: Configure DNS

In your domain registrar's DNS settings, add:

**Type:** `CNAME`
**Name:** `@` (or `www` for www subdomain)
**Value:** `YOUR_USERNAME.github.io`

Or use A records:
- `185.199.108.153`
- `185.199.109.153`
- `185.199.110.153`
- `185.199.111.153`

### Step 4: Update GitHub Pages Settings

1. Go to repository Settings → Pages
2. Under "Custom domain", enter your domain
3. Check "Enforce HTTPS" (after DNS propagates)

## Updating the Website

To update the website:

1. Edit the HTML/CSS files locally
2. Commit and push changes to GitHub
3. Changes will be live within a few minutes

## Testing Locally

You can test the website locally before pushing:

1. Open `index.html` in a web browser
2. Or use a local server:
   ```bash
   cd website
   python -m http.server 8000
   # Then visit http://localhost:8000
   ```

## Support

For issues or questions, please contact through the app's support channels.

