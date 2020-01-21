package nsusbloader.Utilities;

import java.io.*;

public class SplitMergeTool {

    public static Runnable splitFile(String filePath, String saveToPath){
        File file = new File(filePath);
        File folder = new File(saveToPath+File.separator+"!_"+file.getName());

        if (! folder.mkdir()){                  // TODO: PROMPT - remove directory?
            if (folder.exists())
                ;// folder exists - return
            else
                ;// folder not created and not exists - return
        }

        return new Runnable() {
            @Override
            public void run() {
                try{
                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));

                    BufferedOutputStream fragmentBos;

                    long counter;

                    byte[] chunk;

                    main_loop:
                    for (int i = 0; ; i++){
                        fragmentBos = new BufferedOutputStream(
                                new FileOutputStream(new File(folder.getAbsolutePath()+File.separator+String.format("%02d", i)))
                        );

                        counter = 0;

                        while (counter < 512){      // 0xffff0000 total
                            chunk = new byte[8388480];

                            if (bis.read(chunk) < 0){
                                fragmentBos.close();
                                break main_loop;
                            }

                            fragmentBos.write(chunk);

                            counter++;
                        }
                        fragmentBos.close();
                    }

                    bis.close();
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        };
    };

    public static Runnable mergeFile(String filePath, String saveToPath){
        File folder = new File(filePath);
        File resultFile = new File(saveToPath+File.separator+"!_"+folder.getName());
        //BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(resultFile));

        for (File sss : folder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                System.out.println(s);
                return false;
            }
        })){
            System.out.println("|");
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {

            }
        };

        return runnable;
    }
}
