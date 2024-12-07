package com.mycodefu;

import com.mycodefu.datapreparation.util.JsonUtil;
import com.mycodefu.model.Category;
import com.mycodefu.model.Image;
import com.mycodefu.model.ImageSearchResult;
import com.mycodefu.mongodb.CategoryDataAccess;
import com.mycodefu.mongodb.ImageDataAccess;
import com.mycodefu.service.SimpleServer;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

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
                .addGetHandler("/image/search", params -> {
                    String text = params.get("text");
                    if (text == null) {
                        return "Please provide a text parameter";
                    }
                    int page;
                    if (params.containsKey("page")) {
                        page = Integer.parseInt(params.get("page"));
                    } else {
                        page = 0;
                    }
                    Function<String, Boolean> booleanParam = (String key) -> {
                        if (params.containsKey(key)) {
                            return Boolean.parseBoolean(params.get(key));
                        } else {
                            return null;
                        }
                    };
                    Function<String, List<String>> listParam = (String key) -> {
                        if (params.containsKey(key)) {
                            return List.of(params.get(key).split(","));
                        } else {
                            return null;
                        }
                    };
                    ImageDataAccess imageDataAccess = ImageDataAccess.getInstance();
                    ImageSearchResult result = imageDataAccess.search(
                            text,
                            page,
                            booleanParam.apply("hasPerson"),
                            listParam.apply("animal"),
                            listParam.apply("appliance"),
                            listParam.apply("electronic"),
                            listParam.apply("food"),
                            listParam.apply("furniture"),
                            listParam.apply("indoor"),
                            listParam.apply("kitchen"),
                            listParam.apply("outdoor"),
                            listParam.apply("sports"),
                            listParam.apply("vehicle")
                    );
                    return JsonUtil.writeToString(result);
                })
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start();
    }
}
