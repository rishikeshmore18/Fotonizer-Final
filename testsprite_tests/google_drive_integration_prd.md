# Google Drive Integration PRD

## Project Overview
**Project Name**: PhotoApp10 Google Drive Sync  
**Objective**: Implement real Google Drive backup and restore functionality using official Google Drive Java client  
**Approach**: Android-only solution with GoogleAccountCredential (no backend required)

## Current State Analysis
- ✅ Google Sign-In implemented with Drive scope
- ✅ Retrofit-based Drive API calls (to be replaced)
- ✅ DriveSyncManager, DriveUploader, DriveAppData exist
- ❌ AuthManager returns null tokens (401 errors)
- ❌ No real Drive API integration

## Technical Requirements

### 1. Dependencies
- `google-api-client-android:1.35.0` (exclude httpclient)
- `google-http-client-gson:1.43.3` (exclude httpclient)  
- `google-api-services-drive:v3-rev20230809-2.0.0`
- Keep existing: `play-services-auth:21.2.0`

### 2. Authentication Flow
- Use `GoogleAccountCredential.usingOAuth2()` with Drive scope
- Automatic token fetching and refreshing
- No server auth codes or backend required

### 3. Drive API Integration
- Replace Retrofit calls with official Drive client
- Use `files().list()`, `files().create()`, `files().update()`
- Store files in `appDataFolder` (hidden from user)

### 4. Core Features
- **Backup**: Upload `backup.json` + photos to Drive
- **Restore**: Download and restore from Drive
- **Sync**: Incremental updates (only changed files)
- **Error Handling**: Retry on 401/403/429/5xx

## Implementation Plan

### Phase 1: Dependencies & AuthManager
1. Add Google Drive Java client dependencies
2. Update AuthManager to use GoogleAccountCredential
3. Implement buildDriveService() method

### Phase 2: Drive API Integration  
1. Replace DriveAppData Retrofit calls
2. Replace DriveUploader Retrofit calls
3. Update DriveSyncWorker to use Drive client

### Phase 3: Testing & Validation
1. Test OAuth setup with SHA-1 fingerprint
2. Verify backup/restore functionality
3. Test error handling and retry logic

## Success Criteria
- ✅ Real Google Drive backup (not mock)
- ✅ Cross-device restore functionality
- ✅ No 401/403 authentication errors
- ✅ Automatic token refresh
- ✅ All core features remain working

## Risk Mitigation
- OAuth configuration: Ensure SHA-1 fingerprint matches
- Scope permissions: Use only `drive.appdata` scope
- Error handling: Implement proper retry logic
- Fallback: Keep local backup as backup option

## Testing Strategy
- Unit tests for Drive API calls
- Integration tests for backup/restore
- End-to-end tests with real Drive account
- Error scenario testing (network, auth failures)





