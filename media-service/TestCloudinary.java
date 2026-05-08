import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.File;
import java.io.FileOutputStream;

public class TestCloudinary {
    public static void main(String[] args) throws Exception {
        Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", "dlc47gsjp",
            "api_key", "288252612814225",
            "api_secret", "lHdmQcsZ3jnla3KgY_c6cCY4fmg",
            "secure", true
        ));
        File f = new File("test.jpg");
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(new byte[]{ (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xD9 });
        fos.close();
        System.out.println(cloudinary.uploader().upload(f, ObjectUtils.emptyMap()));
    }
}
