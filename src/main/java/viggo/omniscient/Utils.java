package viggo.omniscient;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;

public class Utils {
    static String join(Collection<?> s, String delimiter) {
        StringBuilder builder = new StringBuilder();
        Iterator<?> iter = s.iterator();
        while (iter.hasNext()) {
            builder.append(iter.next());
            if (!iter.hasNext()) {
                break;
            }
            builder.append(delimiter);
        }
        return builder.toString();
    }

    public static Object copyObject(Object orig) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(orig);
        objectOutputStream.flush();
        objectOutputStream.close();

        ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));

        return in.readObject();
    }
}
