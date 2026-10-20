Diagram sources for the Dateroem documentation
==============================================

src/*.mmd   Mermaid diagrams (text). Edit them, then render.
src/*.html  Hand-drawn diagrams (SVG drawn by lib_draw.js).

To re-render on Windows (PowerShell), from this folder:
    .\render.ps1                 # all diagrams
    .\render.ps1 -Only arch_system

Rendering uses Microsoft Edge in headless mode and writes PNG files
(2x resolution) to the "out" folder here. Copy the new PNG over the
matching file in the Diagrams folder and replace the picture in the
Word document (right-click the picture > Change Picture).
