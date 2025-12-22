# PhotoApp10 📸

A modern Android photo management application with cloud backup, album organization, and seamless Google Drive integration.

## 🌟 Features

### 📱 Core Functionality
- **Photo Management**: Capture, organize, and manage photos with native camera integration
- **Album Organization**: Create, edit, and organize photo albums with custom names and emojis
- **Favorites System**: Mark photos as favorites with instant visual feedback
- **Search**: Real-time search across albums and photos
- **Sorting**: Multiple sorting options (date, name, size)

### ☁️ Cloud Integration
- **Google Drive Backup**: Automatic cloud backup with Google Drive API
- **Real-time Sync**: Live sync status indicators and manual sync triggers
- **Local Backup**: Simplified local backup to Documents folder
- **Restore Options**: Merge or replace data during restoration

### 🎨 User Experience
- **Material Design**: Modern, intuitive UI following Material Design principles
- **Personalized Greeting**: Dynamic welcome message using Google account info
- **Responsive Design**: Optimized for all Android screen sizes
- **Dark/Light Themes**: System theme support with user preferences

### 🔐 Security & Privacy
- **Google OAuth2**: Secure authentication with proper scopes
- **App-private Storage**: Photos stored securely in app-private directories
- **File Provider**: Secure URI sharing for camera integration
- **No Data Collection**: No user data sent to external services

## 🛠️ Technical Stack

### Core Technologies
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room with SQLite
- **Dependency Injection**: Manual DI with Modules

### Key Libraries
- **Google Services**: Sign-In, Drive API
- **Image Loading**: Coil for efficient image handling
- **Background Tasks**: WorkManager for sync operations
- **Preferences**: DataStore for user settings
- **Serialization**: Kotlinx Serialization for JSON handling
- **Logging**: Timber for debug logging

## 📋 Prerequisites

- **Android Studio**: Arctic Fox or later
- **Android SDK**: API 24+ (Android 7.0)
- **Kotlin**: 2.0.20+
- **Google Account**: For Drive integration
- **Google Cloud Console**: Project with Drive API enabled

## 🚀 Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/photoapp10.git
cd photoapp10
```

### 2. Google Drive API Setup
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing one
3. Enable Google Drive API
4. Create OAuth 2.0 credentials
5. Download `client_secret_*.json` file
6. Place it in `app/` directory
7. Update `applicationId` in `build.gradle.kts` to match your OAuth client

### 3. Build Configuration
```bash
# Clean and build
./gradlew clean build

# Install debug version
./gradlew installDebug
```

### 4. Permissions
The app requires these permissions (automatically handled):
- `CAMERA`: For photo capture
- `MANAGE_EXTERNAL_STORAGE`: For local backup (Android 11+)

## 📱 Usage

### Getting Started
1. **Sign In**: Use your Google account to authenticate
2. **First Launch**: Choose to restore from cloud backup or start fresh
3. **Create Albums**: Tap the folder icon to create new albums
4. **Take Photos**: Use the camera icon to capture photos
5. **Organize**: Add emojis, mark favorites, and organize your photos

### Cloud Backup
- **Automatic Sync**: Photos sync automatically to Google Drive
- **Manual Sync**: Tap the cloud icon to trigger manual sync
- **Sync Status**: Visual indicators show sync progress
- **Local Backup**: Access via hamburger menu → Local Backup

### Album Management
- **Create**: Tap folder icon with plus sign
- **Edit**: Tap pencil icon on album card
- **Delete**: Tap trash icon on album card
- **Favorites**: Tap star icon to mark as favorite

## 🏗️ Project Structure

```
app/src/main/java/com/example/photoapp10/
├── core/                    # Core utilities and infrastructure
│   ├── camera/             # Camera integration
│   ├── db/                 # Database setup
│   ├── di/                 # Dependency injection
│   ├── file/               # File management
│   └── util/               # Utilities
├── feature/                # Feature modules
│   ├── auth/               # Authentication
│   ├── album/              # Album management
│   ├── photo/              # Photo management
│   ├── backup/             # Cloud & local backup
│   ├── search/             # Search functionality
│   └── settings/           # App settings
└── ui/                     # Shared UI components
    ├── components/         # Reusable components
    └── theme/              # App theming
```

## 🔧 Configuration

### Google Drive API
- **Scope**: `https://www.googleapis.com/auth/drive.appdata`
- **Storage**: Uses appDataFolder for private app data
- **Authentication**: Google Sign-In with OAuth2

### Database Schema
- **Albums**: id, name, emoji, favorite, createdAt, updatedAt
- **Photos**: id, albumId, filename, path, thumbPath, metadata, timestamps

### Backup Format
```json
{
  "createdAt": 1234567890,
  "settings": { "theme": "system", "sort": "date_new" },
  "albums": [...],
  "photos": [...]
}
```

## 🐛 Troubleshooting

### Common Issues

#### Build Errors
- **Gradle Version**: Ensure Gradle 8.13+ is used
- **Dependencies**: Run `./gradlew clean build` to resolve conflicts
- **OAuth Client**: Verify `client_secret_*.json` is in correct location

#### Authentication Issues
- **SHA-1 Fingerprint**: Add your debug keystore SHA-1 to Google Cloud Console
- **Package Name**: Ensure `applicationId` matches OAuth client configuration
- **API Access**: Verify Google Drive API is enabled

#### Sync Problems
- **Network**: Check internet connection
- **Permissions**: Ensure Drive API permissions are granted
- **Storage**: Verify sufficient Google Drive storage space

### Debug Mode
Enable debug logging:
```kotlin
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}
```

## 📊 Performance

### Optimizations
- **Image Loading**: Coil with proper caching and resizing
- **Database**: Room with proper indexing and paging
- **Background Tasks**: WorkManager with proper cancellation
- **Memory Management**: Chunked uploads and proper resource cleanup

### Benchmarks
- **Startup Time**: < 2 seconds on modern devices
- **Photo Loading**: < 500ms for cached thumbnails
- **Sync Performance**: ~10 photos/second upload rate

## 🤝 Contributing

### Development Setup
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Run tests: `./gradlew test`
5. Commit changes: `git commit -m 'Add amazing feature'`
6. Push to branch: `git push origin feature/amazing-feature`
7. Open a Pull Request

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add proper documentation for public APIs
- Write unit tests for business logic

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Google**: For Drive API and Sign-In services
- **Jetpack Compose**: For modern UI framework
- **Android Team**: For excellent development tools
- **Open Source Community**: For various libraries and tools

## 📞 Support

- **Issues**: Report bugs via GitHub Issues
- **Discussions**: Use GitHub Discussions for questions
- **Documentation**: Check this README and inline code comments

## 🔄 Version History

### v1.0.0 (Current)
- Initial release
- Core photo management features
- Google Drive integration
- Local backup system
- Material Design UI

---

**Made with ❤️ for Android developers**

*PhotoApp10 - Your photos, organized beautifully* 📸✨




