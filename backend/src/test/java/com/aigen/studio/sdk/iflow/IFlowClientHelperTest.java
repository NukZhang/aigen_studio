package com.aigen.studio.sdk.iflow;

import cn.iflow.sdk.types.enums.ToolCallStatus;
import cn.iflow.sdk.types.messages.ToolResultMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IFlowClientHelperTest {

    @Test
    void formatToolResultContentHandlesNullContent() throws Exception {
        IFlowClientHelper helper = new IFlowClientHelper();
        ToolResultMessage message = new ToolResultMessage(
                "id-1",
                ToolCallStatus.COMPLETED,
                "tool",
                null,
                null,
                null,
                null,
                null,
                null
        );

        Method method = IFlowClientHelper.class.getDeclaredMethod(
                "formatToolResultContent",
                ToolResultMessage.class
        );
        method.setAccessible(true);

        String result = (String) method.invoke(helper, message);
        assertEquals("", result);
    }

}
