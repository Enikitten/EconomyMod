package com.enigmadevelopment.economymod;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class SQLSerializer {

    /*
    @param conTemplate
        SQLConnectionTemplate to be saved to file
    @param filePath
        Place to save the template file
     */

    public static void serializeSQL (SQLConnectionTemplate conTemplate, Path filePath) throws IOException{
        Files.deleteIfExists(filePath);
        Files.createFile(filePath);
        FileOutputStream fos = new FileOutputStream(new File(filePath.toUri()));
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(conTemplate);
        oos.close();
        fos.close();
    }

    /*
    @param filePath
        Path to the Serialized SQLConnectionTemplate
     */

    public static SQLConnectionTemplate deserializeSQL (Path filePath) throws IOException, ClassNotFoundException {
        return (SQLConnectionTemplate) (new ObjectInputStream(new FileInputStream(new File(filePath.toUri())))).readObject();
    }
}
