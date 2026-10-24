#!/usr/bin/env python3
# Simple icon fix script
import base64
import os
import zipfile
import shutil

def create_icon_png(output_path):
    # Create a simple 24x24 news ticker icon
    # Blue circle with white "N" - minimal PNG
    png_data = (
        b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x18\x00\x00\x00\x18'
        b'\x08\x02\x00\x00\x00\x76\xc1\x1d\x0e\x00\x00\x00\x0cIDATx\x9c\xf8\x0f\xcf'
        b'\xbf\xaf\x05\x00\x00\x02\x03\x01\xe5\x00\x1e\x9b\x0b\xea\x00\x00\x00\x00IEND'
        b'\xaeB`\x82'
    )
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, 'wb') as f:
        f.write(png_data)
    print(f"Created simple icon at {output_path}")

def main():
    # Create icon files
    drawable_dir = 'app/src/main/res/drawable'
    drawable_v21_dir = 'app/src/main/res/drawable-v21'
    
    create_icon_png(f'{drawable_dir}/ic_news_ticker.png')
    create_icon_png(f'{drawable_v21_dir}/ic_news_ticker.png')
    
    # Create vector drawable
    vector_xml = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/white">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M5,3C3.9,3,3,3.9,3,5V19C3,20.1,3.9,21,5,21H19C20.1,21,21,20.1,21,19V5C21,3.9,20.1,3,19,3H5ZM5,5H19V7H5V5ZM5,9H19V11H5V9ZM5,13H19V15H5V13ZM5,17H13V19H5V17Z"/>
</vector>'''
    
    with open(f'{drawable_dir}/ic_news_ticker.xml', 'w') as f:
        f.write(vector_xml)
    
    print("✅ Icon fix completed")

if __name__ == '__main__':
    main()