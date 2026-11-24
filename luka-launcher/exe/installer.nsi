; The luka installer. It needs no administrator rights: everything goes in the player's own folders.
; Built by build-installer.ps1, which passes VERSION, EXE and OUT.
Unicode true
!include "MUI2.nsh"

!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\luka"

Name "luka"
OutFile "${OUT}"
InstallDir "$LOCALAPPDATA\Programs\luka"
InstallDirRegKey HKCU "Software\luka" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
ShowInstDetails nevershow
BrandingText "luka ${VERSION}"

VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "luka"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "FileDescription" "luka installer"
VIAddVersionKey "CompanyName" "luka"
VIAddVersionKey "LegalCopyright" "Copyright (C) 2026 the luka contributors. GPL-3.0-or-later."

!define MUI_ICON "..\res\luka.ico"
!define MUI_UNICON "..\res\luka.ico"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\Luka.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Start luka"

!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Section "luka"
  SetOutPath "$INSTDIR"
  File "/oname=Luka.exe" "${EXE}"
  WriteUninstaller "$INSTDIR\Uninstall luka.exe"
  CreateShortcut "$SMPROGRAMS\luka.lnk" "$INSTDIR\Luka.exe"
  CreateShortcut "$DESKTOP\luka.lnk" "$INSTDIR\Luka.exe"
  WriteRegStr HKCU "Software\luka" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayName" "luka"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "Publisher" "luka"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayIcon" "$INSTDIR\Luka.exe"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "UninstallString" '"$INSTDIR\Uninstall luka.exe"'
  WriteRegStr HKCU "${UNINSTALL_KEY}" "QuietUninstallString" '"$INSTDIR\Uninstall luka.exe" /S'
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoRepair" 1
SectionEnd

Section "Uninstall"
  Delete "$SMPROGRAMS\luka.lnk"
  Delete "$DESKTOP\luka.lnk"
  Delete "$INSTDIR\Luka.exe"
  Delete "$INSTDIR\Uninstall luka.exe"
  RMDir "$INSTDIR"
  RMDir /r "$LOCALAPPDATA\Luka\app"
  DeleteRegKey HKCU "${UNINSTALL_KEY}"
  DeleteRegKey HKCU "Software\luka"
  MessageBox MB_YESNO|MB_ICONQUESTION|MB_DEFBUTTON2 "Also delete your luka game files, settings, screenshots and saved accounts?" /SD IDNO IDNO keep
  RMDir /r "$LOCALAPPDATA\Luka"
  keep:
SectionEnd
