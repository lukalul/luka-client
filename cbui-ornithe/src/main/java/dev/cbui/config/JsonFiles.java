package dev.cbui.config;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class JsonFiles {
    public static JsonObject read(File source)throws IOException{try(Reader reader=new InputStreamReader(new FileInputStream(source),StandardCharsets.UTF_8)){return new JsonParser().parse(reader).getAsJsonObject();}}
    public static void writeAtomic(File destination,JsonObject root)throws IOException{
        File parent=destination.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Could not create "+parent);
        File temp=new File(destination.getPath()+".tmp");try(Writer writer=new OutputStreamWriter(new FileOutputStream(temp),StandardCharsets.UTF_8)){new GsonBuilder().setPrettyPrinting().create().toJson(root,writer);}
        if(destination.exists()&&!destination.delete())throw new IOException("Could not replace "+destination);if(!temp.renameTo(destination))throw new IOException("Could not move config into place");
    }
    private JsonFiles(){ }
}
