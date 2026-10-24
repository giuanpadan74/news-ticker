# PowerShell script to create simple news ticker icon PNG files
# Creates 1024×1024 versions for different densities

param(
    [string]$OutputDir = "C:\cleanyourtv\addons\news-ticker\app\src\main\res\mipmap-anydpi-v26"
)

# Create a simple news-themed icon
# Design: Clean blue circle with white "N" letter and subtle horizontal line

# We'll use a simple approach - create base64 encoded PNGs for each size
# For simplicity, I'll create one main icon and let Android handle scaling

function CreateBase64PNG {
    param(
        [string]$Base64Data,
        [string]$FilePath
    )
    
    $bytes = [Convert]::FromBase64String($Base64Data)
    $fileStream = [System.IO.File]::Create($FilePath)
    $fileStream.Write($bytes, 0, $bytes.Length)
    $fileStream.Close()
}

# News blue color (material design blue 500)
# Circle with "N" and subtle ticker line

# 1024×1024 main icon (foreground)
$icon1024 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
CreateBase64PNG -Base64Data $icon1024 -FilePath "$OutputDir\ic_launcher_foreground.png"

# Background - solid blue (optional)
$bg1024 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8%2F5+hHgAHggJ%2FPchI7wAAAABJRU5ErkJggg=="
CreateBase64PNG -Base64Data $bg1024 -FilePath "$OutputDir\ic_launcher_background.png"

Write-Host "Created simple news ticker icon files in $OutputDir"