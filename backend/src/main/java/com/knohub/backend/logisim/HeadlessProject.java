package com.knohub.backend.logisim;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.proj.Project;

/**
 * Lightweight Project that allows injecting a selection without requiring a GUI frame.
 */
public class HeadlessProject extends Project {
    private Selection selection;

    public HeadlessProject(LogisimFile file) {
        super(file);
    }

    public void attachSelection(Canvas canvas) {
        this.selection = canvas.getSelection();
    }

    @Override
    public Selection getSelection() {
        if (selection != null) {
            return selection;
        }
        return super.getSelection();
    }
}
