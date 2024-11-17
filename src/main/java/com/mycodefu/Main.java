package com.mycodefu;

import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.model.Category;
import com.mycodefu.model.Image;
import com.mycodefu.mongodb.CategoryDataAccess;
import com.mycodefu.mongodb.ImageDataAccess;
import com.mycodefu.service.SimpleServer;

import java.io.IOException;
import java.util.List;

import static com.mycodefu.datapreparation.PrepareDataEntryPoint.downloadAndInitialiseDataset;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            switch(args[0]) {
                case "--loadData":
                    downloadAndInitialiseDataset();
                    break;
                default:
                    System.out.println("Unsupported argument. Supported arguments: --loadData");
            }
        }

        SimpleServer server = SimpleServer.create(8222)
                .addGetHandler("/categories", params -> {
                    List<Category> categories = CategoryDataAccess.getInstance().list();
                    return JsonUtil.writeToString(categories);
                })
                .addGetHandler("/image", params -> {
                    String id = params.get("id");
                    if (id == null) {
                        return "Please provide an id parameter";
                    }
                    Image image = ImageDataAccess.getInstance().get(Integer.parseInt(id));
                    return JsonUtil.writeToString(image);
                })
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();
    }
}