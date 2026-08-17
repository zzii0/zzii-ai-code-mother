package com.nylg.zziiaicodemother.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 对话历史 txt 导出模式。
 */
@Getter
public enum ChatHistoryExportModeEnum {

    FULL("完整版", "full"),
    COMPACT("精简版", "compact");

    private final String text;
    private final String value;

    ChatHistoryExportModeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static ChatHistoryExportModeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (ChatHistoryExportModeEnum modeEnum : ChatHistoryExportModeEnum.values()) {
            if (modeEnum.value.equalsIgnoreCase(value)) {
                return modeEnum;
            }
        }
        return null;
    }
}
