package jd.plugins.optional.customizer;

import java.awt.event.ActionEvent;
import java.util.ArrayList;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;

import jd.config.SubConfiguration;
import jd.gui.UserIO;
import jd.gui.swing.GuiRunnable;
import jd.gui.swing.jdgui.actions.ThreadedAction;
import jd.gui.swing.jdgui.interfaces.SwitchPanel;
import jd.gui.swing.jdgui.views.toolbar.ViewToolbar;
import jd.nutils.JDFlags;
import jd.utils.locale.JDL;
import net.miginfocom.swing.MigLayout;

public class CustomizerGui extends SwitchPanel {

    private static final long serialVersionUID = 7508784076121700378L;

    private final SubConfiguration config;

    private CustomizerTable table;

    public CustomizerGui(SubConfiguration config) {
        this.config = config;

        initActions();
        initGUI();
    }

    private void initGUI() {
        this.setLayout(new MigLayout("ins 5, wrap 1", "[grow,fill]", "[][grow,fill]"));
        ViewToolbar vt = new ViewToolbar() {
            private static final long serialVersionUID = -2194834048392779383L;

            @Override
            public void setDefaults(int i, AbstractButton ab) {
                ab.setForeground(new JLabel().getForeground());
            }
        };
        vt.setList(new String[] { "action.customize.addsetting", "action.customize.removesetting" });

        this.add(vt, "dock north,gapleft 3");
        this.add(new JScrollPane(table = new CustomizerTable(config.getGenericProperty(JDPackageCustomizer.PROPERTY_SETTINGS, new ArrayList<CustomizeSetting>()))), "growx,growy");
    }

    private void initActions() {
        new ThreadedAction("action.customize.addsetting", "gui.images.add") {
            private static final long serialVersionUID = 2902582906883565245L;

            @Override
            public void initDefaults() {
                this.setToolTipText(JDL.L("action.customize.addsetting.tooltip", "Add a new Setting"));
            }

            @Override
            public void init() {
            }

            @Override
            public void threadedActionPerformed(final ActionEvent e) {
                table.editingStopped(null);
                new GuiRunnable<Object>() {

                    @Override
                    public Object runSave() {
                        String result = UserIO.getInstance().requestInputDialog(JDL.L("action.customize.addsetting.ask", "Please insert the name for the new Setting:"));
                        if (result != null) {
                            table.getModel().getSettings().add(new CustomizeSetting(result));
                            table.getModel().refreshModel();
                        }
                        return null;
                    }
                }.start();

            }
        };

        new ThreadedAction("action.customize.removesetting", "gui.images.delete") {
            private static final long serialVersionUID = -961227177718839351L;

            @Override
            public void initDefaults() {
                this.setToolTipText(JDL.L("action.customize.removesetting.tooltip", "Remove selected Setting(s)"));
            }

            @Override
            public void init() {
            }

            @Override
            public void threadedActionPerformed(ActionEvent e) {
                int[] rows = table.getSelectedRows();
                table.editingStopped(null);
                if (rows.length == 0) return;
                if (JDFlags.hasSomeFlags(UserIO.getInstance().requestConfirmDialog(0, JDL.LF("action.customize.removesetting.ask", "Remove selected Setting(s)? (%s Account(s))", rows.length)), UserIO.RETURN_OK, UserIO.RETURN_DONT_SHOW_AGAIN)) {
                    ArrayList<CustomizeSetting> settings = table.getModel().getSettings();
                    for (int i = rows.length - 1; i >= 0; --i) {
                        settings.remove(rows[i]);
                    }
                }
                table.getModel().refreshModel();
            }
        };
    }

    @Override
    protected void onHide() {
        config.setProperty(JDPackageCustomizer.PROPERTY_SETTINGS, table.getModel().getSettings());
        config.save();
    }

    @Override
    protected void onShow() {
        table.getModel().setSettings(config.getGenericProperty(JDPackageCustomizer.PROPERTY_SETTINGS, new ArrayList<CustomizeSetting>()));
        table.getModel().refreshModel();
    }

}
