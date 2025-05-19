package com.example.plugin;

import java.util.List;
import java.util.ArrayList;

public class MethodMetadata {
    public final List<String> params;
    public final String version;

    public MethodMetadata(String param, String version) {
        this.params = new ArrayList<>();
        this.params.add(param);
        this.version = version;
    }

    public void addParam(String param) {
        if (!params.contains(param)) {
            this.params.add(param);
        }
    }

    public List<String> getParams() {
        return params;
    }

    public String getVersion() {
        return version;
    }
}
