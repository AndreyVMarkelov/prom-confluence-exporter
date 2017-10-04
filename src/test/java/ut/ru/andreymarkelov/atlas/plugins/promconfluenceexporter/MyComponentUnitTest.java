package ut.ru.andreymarkelov.atlas.plugins.promconfluenceexporter;

import org.junit.Test;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.api.MyPluginComponent;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.impl.MyPluginComponentImpl;

import static org.junit.Assert.assertEquals;

public class MyComponentUnitTest
{
    @Test
    public void testMyName()
    {
        MyPluginComponent component = new MyPluginComponentImpl(null);
        assertEquals("names do not match!", "myComponent",component.getName());
    }
}