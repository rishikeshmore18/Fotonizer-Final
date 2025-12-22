# Foto-Nizer Website Setup Guide

## What Was Created

Your website includes:
- ✅ **index.html** - Beautiful landing page with app features
- ✅ **terms.html** - Complete Terms and Conditions
- ✅ **privacy.html** - Comprehensive Privacy Policy
- ✅ **styles.css** - Modern, responsive design
- ✅ **README.md** - Detailed hosting instructions

## Quick Start (5 Minutes)

### 1. Create GitHub Repository
1. Go to https://github.com/new
2. Repository name: `foto-nizer-website`
3. Make it **Public**
4. Click "Create repository"

### 2. Upload Files
1. In your new repository, click "Add file" → "Upload files"
2. Upload these files from the `website` folder:
   - `index.html`
   - `terms.html`
   - `privacy.html`
   - `styles.css`
3. Click "Commit changes"

### 3. Enable GitHub Pages
1. Go to repository **Settings** → **Pages**
2. Source: **Deploy from a branch**
3. Branch: **main** / **root**
4. Click **Save**

### 4. Your Website is Live!
Your site will be at:
```
https://YOUR_USERNAME.github.io/foto-nizer-website/
```

## Update Your App

After your website is live, update `AboutUsScreen.kt`:

1. Open: `app/src/main/java/com/example/photoapp10/feature/about/ui/AboutUsScreen.kt`
2. Replace `YOUR_USERNAME` with your actual GitHub username in the URLs
3. Or use your custom domain if you have one

Example:
```kotlin
openUrl(context, "https://johndoe.github.io/foto-nizer-website/terms.html")
```

## Custom Domain (Optional)

If you want `foto-nizer.com` instead of `github.io`:

1. Register domain (Namecheap, Google Domains, etc.)
2. Create `CNAME` file in repository with your domain
3. Configure DNS at your registrar
4. Wait for DNS propagation (5-30 minutes)

See `README.md` for detailed instructions.

## Design Features

- ✨ Modern gradient hero section
- 📱 Fully responsive (mobile, tablet, desktop)
- 🎨 Material Design inspired
- ⚡ Fast loading
- 🔍 SEO optimized
- ♿ Accessible

## Need Help?

Check `README.md` for detailed instructions and troubleshooting.

