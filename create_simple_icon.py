#!/usr/bin/env python3
# Create a simple news ticker icon using Python's PIL if available
import base64
import os

def create_simple_icon():
    # Simple approach: create a 24x24 icon with blue background and white "N"
    # This is a minimal valid PNG
    
    # Create a 24x24 PNG with simple blue background
    # Blue: #2196F3, White: #FFFFFF
    
    # PNG header
    png_header = bytes([
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,  # Signature
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,  # IHDR
        0x00, 0x00, 0x00, 0x18, 0x00, 0x00, 0x00, 0x18,  # 24x24
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x63, 0xF6   # Color type=2, etc.
    ])
    
    # Simple pixel data: alternating blue and white to form an "N"
    # Each row starts with filter byte (0 = none)
    def create_row(color_byte, repeat_count):
        return bytes([0]) + bytes([color_byte] * repeat_count)
    
    # Create 24x24 pixel data
    rows = []
    for y in range(24):
        row = []
        for x in range(24):
            # Create an "N" shape: blue where x < y or x > y, white in middle
            if x < y or x > y or x == 23:
                # Blue
                row.append(0x42)  # Blue
            else:
                # White
                row.append(0xFF)
        rows.append(bytes([0]) + bytes(row))  # Filter byte + pixels
    
    # Combine all rows
    idat_data = b''.join(rows)
    
    # Build complete PNG
    png_data = png_header + idat_data
    
    # Write to file
    output_path = "app/src/main/res/drawable-v21/ic_news_ticker.png"
    with open(output_path, 'wb') as f:
        f.write(png_data)
    
    print(f"Created simple icon at {output_path}")
    return output_path

if __name__ == "__main__":
    create_simple_icon()