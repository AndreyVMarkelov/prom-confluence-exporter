package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.util;

import javax.servlet.ServletException;
import java.io.IOException;

@FunctionalInterface
public interface ExceptionRunnable {
    void run() throws IOException, ServletException;
}
