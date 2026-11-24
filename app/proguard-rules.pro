
# Tazieh Viewer hardening: keep Android/Room/Compose reflection metadata as needed.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-dontwarn javax.annotation.**
# PdfBox-Android treats JPEG2000/JPX decoding as an optional feature.
# Without the optional JP2Android library, JPX images are ignored by PDFBox;
# PDF text extraction remains available. Suppress R8's missing optional class error.
-dontwarn com.gemalto.jp2.**
