import sparta.checkers.permission.qual.RequiredPermissions;
import static android.Manifest.permission.NFC;

class InAnnotation {
    //:: error: (annotation.type.incompatible)
    @RequiredPermissions("Hi!")
    void bad1() {}

    @RequiredPermissions(permission.NFC)
    void good1() {}
}