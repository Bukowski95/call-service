package com.onextel.CallServiceApplication.util;

public class HostNameProvider {

    public static String getHostname() {
        return System.getenv().getOrDefault("HOSTNAME",
                System.getenv().getOrDefault("COMPUTERNAME", "unknown-host"));
    }
}
