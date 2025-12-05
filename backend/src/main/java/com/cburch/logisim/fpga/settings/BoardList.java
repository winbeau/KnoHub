package com.cburch.logisim.fpga.settings;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.util.Collections;
import java.util.List;

/**
 * Minimal stub to satisfy Logisim classes when running headless without FPGA board definitions.
 * Only the methods invoked during circuit loading are implemented with safe defaults.
 */
public class BoardList {

    public BoardList() {
    }

    public boolean addExternalBoard(String boardPath) {
        return false;
    }

    public boolean removeExternalBoard(String boardPath) {
        return false;
    }

    public String getBoardFilePath(String boardName) {
        return "";
    }

    public List<String> getBoardNames() {
        return Collections.singletonList("dummy-board");
    }

    public String getSelectedBoardFileName() {
        return "dummy-board";
    }

    public JComboBox<String> boardSelector() {
        return new JComboBox<>(new String[]{"dummy-board"});
    }

    public JPanel addRemovePanel() {
        return new JPanel();
    }

    public static String getBoardName(String path) {
        return path;
    }
}
