package com.jarvis.ai;

import com.jarvis.util.Json;

import java.util.List;

/** Result of a health check on the reasoning engine. */
public record LlmStatus(String provider, String model, boolean online, boolean modelInstalled, String detail,
                        List<String> installedModels) implements Json.JsonSerializable {

    /** True when the engine can actually answer. */
    public boolean ready() {
        return online && modelInstalled;
    }

    @Override
    public String toJson() {
        return Json.object()
                .put("provider", provider)
                .put("model", model)
                .put("online", online)
                .put("modelInstalled", modelInstalled)
                .put("ready", ready())
                .put("detail", detail)
                .putValue("installedModels", installedModels)
                .toJson();
    }
}
