//    jDownloader - Downloadmanager
//    Copyright (C) 2011  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.captcha.JACMethod;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.HTMLParser;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "fik1.com" }, urls = { "http://(www\\.)?fik1\\.com/[a-z0-9]{12}" }, flags = { 2 })
public class Fik1Com extends PluginForHost {

    private String              BRBEFORE      = "";

    private static final String PASSWORDTEXT0 = "<br><b>Password:</b> <input";

    private static final String PASSWORDTEXT1 = "<br><b>Passwort:</b> <input";

    private static final String COOKIE_HOST   = "http://fik1.com";

    public boolean              NOPREMIUM     = false;
    public Fik1Com(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(COOKIE_HOST + "/premium.html");
    }
    public void checkErrors(DownloadLink theLink, boolean checkAll, String passCode) throws NumberFormatException, PluginException {
        if (checkAll) {
            if (BRBEFORE.contains("<br><b>Password:</b> <input") || BRBEFORE.contains("<br><b>Passwort:</b> <input") || BRBEFORE.contains("Wrong password")) {
                logger.warning("Wrong password, the entered password \"" + passCode + "\" is wrong, retrying...");
                theLink.setProperty("pass", null);
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            if (BRBEFORE.contains("Wrong captcha")) {
                logger.warning("Wrong captcha or wrong password!");
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            if (BRBEFORE.contains("\">Skipped countdown<")) throw new PluginException(LinkStatus.ERROR_FATAL, "Fatal countdown error (countdown skipped)");
        }
        // Some waittimes...
        if (BRBEFORE.contains("You have to wait")) {
            int minutes = 0, seconds = 0, hours = 0;
            String tmphrs = new Regex(BRBEFORE, "You have to wait.*?\\s+(\\d+)\\s+hours?").getMatch(0);
            if (tmphrs != null) hours = Integer.parseInt(tmphrs);
            String tmpmin = new Regex(BRBEFORE, "You have to wait.*?\\s+(\\d+)\\s+minutes?").getMatch(0);
            if (tmpmin != null) minutes = Integer.parseInt(tmpmin);
            String tmpsec = new Regex(BRBEFORE, "You have to wait.*?\\s+(\\d+)\\s+seconds?").getMatch(0);
            if (tmpsec != null) seconds = Integer.parseInt(tmpsec);
            int waittime = ((3600 * hours) + (60 * minutes) + seconds + 1) * 1000;
            if (waittime != 0) {
                logger.info("Detected waittime #1, waiting " + waittime + " milliseconds");
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, waittime);
            } else {
                logger.info("Waittime regexes seem to be broken");
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED);
            }
        }
        if (BRBEFORE.contains("You have reached the download-limit")) {
            String tmphrs = new Regex(BRBEFORE, "\\s+(\\d+)\\s+hours?").getMatch(0);
            String tmpmin = new Regex(BRBEFORE, "\\s+(\\d+)\\s+minutes?").getMatch(0);
            String tmpsec = new Regex(BRBEFORE, "\\s+(\\d+)\\s+seconds?").getMatch(0);
            String tmpdays = new Regex(BRBEFORE, "\\s+(\\d+)\\s+days?").getMatch(0);
            if (tmphrs == null && tmpmin == null && tmpsec == null && tmpdays == null) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, 60 * 60 * 1000l);
            } else {
                int minutes = 0, seconds = 0, hours = 0, days = 0;
                if (tmphrs != null) hours = Integer.parseInt(tmphrs);
                if (tmpmin != null) minutes = Integer.parseInt(tmpmin);
                if (tmpsec != null) seconds = Integer.parseInt(tmpsec);
                if (tmpdays != null) days = Integer.parseInt(tmpdays);
                int waittime = ((days * 24 * 3600) + (3600 * hours) + (60 * minutes) + seconds + 1) * 1000;
                logger.info("Detected waittime #2, waiting " + waittime + "milliseconds");
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, waittime);
            }
        }
        if (BRBEFORE.contains("You're using all download slots for IP")) { throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, 10 * 60 * 1001l); }
        if (BRBEFORE.contains("Error happened when generating Download Link")) throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error!", 10 * 60 * 1000l);
        // Errorhandling for only-premium links
        if (BRBEFORE.contains(" can download files up to ") || BRBEFORE.contains("Upgrade your account to download bigger files") || BRBEFORE.contains(">Upgrade your account to download larger files") || BRBEFORE.contains(">The file You requested  reached max downloads limit for Free Users") || BRBEFORE.contains("Please Buy Premium To download this file<") || BRBEFORE.contains("This file reached max downloads limit")) {
            String filesizelimit = new Regex(BRBEFORE, "You can download files up to(.*?)only").getMatch(0);
            if (filesizelimit != null) {
                filesizelimit = filesizelimit.trim();
                logger.warning("As free user you can download files up to " + filesizelimit + " only");
                throw new PluginException(LinkStatus.ERROR_FATAL, "Free users can only download files up to " + filesizelimit);
            } else {
                logger.warning("Only downloadable via premium");
                throw new PluginException(LinkStatus.ERROR_FATAL, "Only downloadable via premium");
            }
        }
    }
    public void checkServerErrors() throws NumberFormatException, PluginException {
        if (BRBEFORE.contains("No file")) throw new PluginException(LinkStatus.ERROR_FATAL, "Server error");
        if (BRBEFORE.contains("File Not Found") || BRBEFORE.contains("<h1>404 Not Found</h1>")) {
            logger.warning("Server says link offline, please recheck that!");
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
    }
    private String decodeDownloadLink(String s) {
        String decoded = null;

        try {
            Regex params = new Regex(s, "\\'(.*?[^\\\\])\\',(\\d+),(\\d+),\\'(.*?)\\'");

            String p = params.getMatch(0).replaceAll("\\\\", "");
            int a = Integer.parseInt(params.getMatch(1));
            int c = Integer.parseInt(params.getMatch(2));
            String[] k = params.getMatch(3).split("\\|");

            while (c != 0) {
                c--;
                if (k[c].length() != 0) p = p.replaceAll("\\b" + Integer.toString(c, a) + "\\b", k[c]);
            }

            decoded = p;
        } catch (Exception e) {
        }

        String finallink = null;
        if (decoded != null) {
            finallink = new Regex(decoded, "name=\"src\"value=\"(.*?)\"").getMatch(0);
            if (finallink == null) {
                finallink = new Regex(decoded, "type=\"video/divx\"src=\"(.*?)\"").getMatch(0);
                if (finallink == null) finallink = new Regex(decoded, "s1\\.addVariable\\(\\'file\\',\\'(http://.*?)\\'\\)").getMatch(0);
            }
        }
        return finallink;
    }

    public void doFree(DownloadLink downloadLink, boolean resumable, int maxchunks) throws Exception, PluginException {
        String dllink = null;
        String passCode = null;
        if (BRBEFORE.contains("\"download1\"")) {
            br.postPage(downloadLink.getDownloadURL(), "op=download1&usr_login=&id=" + new Regex(downloadLink.getDownloadURL(), COOKIE_HOST.replace("http://", "") + "/" + "([a-z0-9]{12})").getMatch(0) + "&fname=" + Encoding.urlEncode(downloadLink.getName()) + "&referer=&method_free=Free+Download");
            doSomething();
        }
        checkErrors(downloadLink, false, passCode);
        String md5hash = new Regex(BRBEFORE, "<b>MD5.*?</b>.*?nowrap>(.*?)<").getMatch(0);
        if (md5hash != null) {
            md5hash = md5hash.trim();
            logger.info("Found md5hash: " + md5hash);
            downloadLink.setMD5Hash(md5hash);
        }
        br.setFollowRedirects(false);
        // Videolinks can already be found here, if a link is found here we can
        // skip waittimes and captchas
        dllink = getDllink();
        if (dllink == null) {
            Form DLForm = br.getFormbyProperty("name", "F1");
            if (DLForm == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            long timeBefore = System.currentTimeMillis();
            boolean password = false;
            boolean recaptcha = false;
            if (BRBEFORE.contains(PASSWORDTEXT0) || BRBEFORE.contains(PASSWORDTEXT1)) {
                password = true;
                logger.info("The downloadlink seems to be password protected.");
            }

            /* Captcha START */
            if (BRBEFORE.contains(";background:#ccc;text-align")) {
                logger.info("Detected captcha method \"plaintext captchas\" for this host");
                // Captcha method by ManiacMansion
                String[][] letters = new Regex(Encoding.htmlDecode(br.toString()), "<span style='position:absolute;padding-left:(\\d+)px;padding-top:\\d+px;'>(\\d)</span>").getMatches();
                if (letters == null || letters.length == 0) {
                    logger.warning("plaintext captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                SortedMap<Integer, String> capMap = new TreeMap<Integer, String>();
                for (String[] letter : letters) {
                    capMap.put(Integer.parseInt(letter[0]), letter[1]);
                }
                StringBuilder code = new StringBuilder();
                for (String value : capMap.values()) {
                    code.append(value);
                }
                DLForm.put("code", code.toString());
                logger.info("Put captchacode " + code.toString() + " obtained by captcha metod \"plaintext captchas\" in the form.");
            } else if (BRBEFORE.contains("/captchas/")) {
                logger.info("Detected captcha method \"Standard captcha\" for this host");
                String[] sitelinks = HTMLParser.getHttpLinks(br.toString(), null);
                String captchaurl = null;
                if (sitelinks == null || sitelinks.length == 0) {
                    logger.warning("Standard captcha captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                for (String link : sitelinks) {
                    if (link.contains("/captchas/")) {
                        captchaurl = link;
                        break;
                    }
                }
                if (captchaurl == null) {
                    logger.warning("Standard captcha captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                String code = getCaptchaCode(captchaurl, downloadLink);
                DLForm.put("code", code);
                logger.info("Put captchacode " + code + " obtained by captcha metod \"Standard captcha\" in the form.");
            } else if (BRBEFORE.contains("api.recaptcha.net") || BRBEFORE.contains("google.com/recaptcha/api/")) {
                // Some hosters also got commentfields with captchas, therefore
                // is
                // the !br.contains...check Exampleplugin:
                // FileGigaCom
                logger.info("Detected captcha method \"Re Captcha\" for this host");
                PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
                rc.parse();
                rc.load();
                File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                String c = getCaptchaCode(cf, downloadLink);
                if (password) {
                    passCode = handlePassword(passCode, rc.getForm(), downloadLink);
                }
                recaptcha = true;
                waitTime(timeBefore, downloadLink);
                rc.setCode(c);
                logger.info("Put captchacode " + c + " obtained by captcha metod \"Re Captcha\" in the form and submitted it.");
                dllink = br.getRedirectLocation();
            }
            /* Captcha END */

            // If the hoster uses Re Captcha the form has already been sent
            // before
            // here so here it's checked. Most hosters don't use Re Captcha so
            // usually recaptcha is false
            if (!recaptcha) {
                if (password) {
                    passCode = handlePassword(passCode, DLForm, downloadLink);
                }
                waitTime(timeBefore, downloadLink);
                br.submitForm(DLForm);
                logger.info("Submitted DLForm");
            }
            doSomething();
            checkErrors(downloadLink, true, passCode);
            dllink = getDllink();
            if (dllink == null) {
                logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        logger.info("Final downloadlink = " + dllink + " starting the download...");
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, resumable, maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            checkServerErrors();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (passCode != null) {
            downloadLink.setProperty("pass", passCode);
        }
        dl.startDownload();
    }

    // Removed fake messages which can kill the plugin
    public void doSomething() throws NumberFormatException, PluginException {
        BRBEFORE = br.toString();
        ArrayList<String> someStuff = new ArrayList<String>();
        ArrayList<String> regexStuff = new ArrayList<String>();
        regexStuff.add("<\\!(\\-\\-.*?\\-\\-)>");
        regexStuff.add("(display: none;\">.*?</div>)");
        regexStuff.add("(visibility:hidden>.*?<)");
        for (String aRegex : regexStuff) {
            String lolz[] = br.getRegex(aRegex).getColumn(0);
            if (lolz != null) {
                for (String dingdang : lolz) {
                    someStuff.add(dingdang);
                }
            }
        }
        for (String fun : someStuff) {
            BRBEFORE = BRBEFORE.replace(fun, "");
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        try {
            login(account);
        } catch (PluginException e) {
            account.setValid(false);
            return ai;
        }
        String space = br.getRegex(Pattern.compile("<td>Used space:</td>.*?<td.*?b>([0-9\\.]+) of [0-9\\.]+ (Mb|GB)</b>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE)).getMatch(0);
        if (space != null) ai.setUsedSpace(space.trim() + " Mb");
        String points = br.getRegex(Pattern.compile("<td>You have collected:</td.*?b>(.*?)premium points", Pattern.CASE_INSENSITIVE)).getMatch(0);
        if (points != null) {
            // Who needs half points ? If we have a dot in the points, just
            // remove it
            if (points.contains(".")) {
                String dot = new Regex(points, ".*?(\\.(\\d+))").getMatch(0);
                points = points.replace(dot, "");
            }
            ai.setPremiumPoints(Long.parseLong(points.trim()));
        }
        account.setValid(true);
        String availabletraffic = new Regex(BRBEFORE, "Traffic available.*?:</TD><TD><b>(.*?)</b>").getMatch(0);
        if (availabletraffic != null && !availabletraffic.contains("nlimited") && !availabletraffic.equals(" Mb")) {
            ai.setTrafficLeft(SizeFormatter.getSize(availabletraffic));
        } else {
            ai.setUnlimitedTraffic();
        }
        if (!NOPREMIUM) {
            String expire = new Regex(BRBEFORE, "<td>Premium-Account expire:</td>.*?<td>(.*?)</td>").getMatch(0);
            if (expire == null) {
                ai.setExpired(true);
                account.setValid(false);
                return ai;
            } else {
                expire = expire.replaceAll("(<b>|</b>)", "");
                ai.setValidUntil(TimeFormatter.getMilliSeconds(expire, "dd MMMM yyyy", null));
            }
            ai.setStatus("Premium User");
        } else {
            ai.setStatus("Registered (free) User");
        }
        return ai;
    }

    // XfileSharingProBasic Version 2.1.1.4
    @Override
    public String getAGBLink() {
        return COOKIE_HOST + "/tos.html";
    }

    public String getDllink() {
        String dllink = br.getRedirectLocation();
        if (dllink == null) {
            dllink = new Regex(BRBEFORE, "dotted #bbb;padding.*?<a href=\"(.*?)\"").getMatch(0);
            if (dllink == null) {
                dllink = new Regex(BRBEFORE, "This (direct link|download link) will be available for your IP.*?href=\"(http.*?)\"").getMatch(1);
                if (dllink == null) {
                    dllink = new Regex(BRBEFORE, "Download: <a href=\"(.*?)\"").getMatch(0);
                    if (dllink == null) {
                        String cryptedScripts[] = br.getRegex("p}\\((.*?)\\.split\\('\\|'\\)").getColumn(0);
                        if (cryptedScripts != null && cryptedScripts.length != 0) {
                            for (String crypted : cryptedScripts) {
                                dllink = decodeDownloadLink(crypted);
                                if (dllink != null) break;
                            }
                        }
                    }
                }
            }
        }
        return dllink;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return 10;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, true, -2);
    }

    public String handlePassword(String passCode, Form pwform, DownloadLink thelink) throws IOException, PluginException {
        if (thelink.getStringProperty("pass", null) == null) {
            passCode = Plugin.getUserInput("Password?", thelink);
        } else {
            /* gespeicherten PassCode holen */
            passCode = thelink.getStringProperty("pass", null);
        }
        pwform.put("password", passCode);
        logger.info("Put password \"" + passCode + "\" entered by user in the DLForm.");
        return passCode;
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        String passCode = null;
        requestFileInformation(link);
        login(account);
        br.setCookie(COOKIE_HOST, "lang", "english");
        br.setFollowRedirects(false);
        br.getPage(link.getDownloadURL());
        doSomething();
        if (NOPREMIUM) {
            doFree(link, true, -2);
        } else {
            String dllink = br.getRedirectLocation();
            if (dllink == null) {
                doSomething();
                Form DLForm = br.getFormbyProperty("name", "F1");
                if (DLForm == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                if (BRBEFORE.contains(PASSWORDTEXT0) || BRBEFORE.contains(PASSWORDTEXT1)) {
                    passCode = handlePassword(passCode, DLForm, link);
                }
                br.submitForm(DLForm);
                doSomething();
                dllink = br.getRedirectLocation();
                if (dllink == null) {
                    checkErrors(link, true, passCode);
                    dllink = getDllink();
                }
            }
            if (dllink == null) {
                logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            logger.info("Final downloadlink = " + dllink + " starting the download...");
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, -2);
            if (passCode != null) {
                link.setProperty("pass", passCode);
            }
            if (dl.getConnection().getContentType().contains("html")) {
                logger.warning("The final dllink seems not to be a file!");
                br.followConnection();
                checkServerErrors();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        }
    }

    // do not add @Override here to keep 0.* compatibility
    public boolean hasAutoCaptcha() {
        return JACMethod.hasMethod("recaptcha");
    }

    // do not add @Override here to keep 0.* compatibility
    public boolean hasCaptcha() {
        return true;
    }

    private void login(Account account) throws Exception {
        this.setBrowserExclusive();
        br.setCookie(COOKIE_HOST, "lang", "english");
        br.getPage(COOKIE_HOST + "/login.html");
        Form loginform = br.getForm(0);
        if (loginform == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        loginform.put("login", Encoding.urlEncode(account.getUser()));
        loginform.put("password", Encoding.urlEncode(account.getPass()));
        br.submitForm(loginform);
        if (br.getCookie(COOKIE_HOST, "login") == null || br.getCookie(COOKIE_HOST, "xfss") == null) throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
        br.getPage(COOKIE_HOST + "/?op=my_account");
        doSomething();
        if (!BRBEFORE.contains("Premium-Account expire") && !BRBEFORE.contains("Upgrade to premium") && !br.containsHTML(">Renew premium<")) throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
        if (!BRBEFORE.contains("Premium-Account expire") && !br.containsHTML(">Renew premium<")) NOPREMIUM = true;
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(false);
        br.setCookie(COOKIE_HOST, "lang", "english");
        br.getPage(link.getDownloadURL());
        doSomething();
        if (BRBEFORE.contains("No such file") || BRBEFORE.contains("No such user exist") || BRBEFORE.contains("File not found") || BRBEFORE.contains(">File Not Found<")) {
            logger.warning("file is 99,99% offline, throwing \"file not found\" now...");
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = new Regex(BRBEFORE, "You have requested.*?http://.*?[a-z0-9]{12}/(.*?)</font>").getMatch(0);
        if (filename == null) {
            filename = new Regex(BRBEFORE, "fname\"( type=\"hidden\")? value=\"(.*?)\"").getMatch(1);
            if (filename == null) {
                filename = new Regex(BRBEFORE, "<h2>Download File(.*?)</h2>").getMatch(0);
                if (filename == null) {
                    filename = new Regex(BRBEFORE, "Filename:</b></td><td[ ]{0,2}>(.*?)</td>").getMatch(0);
                    if (filename == null) {
                        filename = new Regex(BRBEFORE, "Filename.*?nowrap.*?>(.*?)</td").getMatch(0);
                        if (filename == null) {
                            filename = new Regex(BRBEFORE, "File Name.*?nowrap>(.*?)</td").getMatch(0);
                        }
                    }
                }
            }
        }
        String filesize = new Regex(BRBEFORE, "\\(([0-9]+ bytes)\\)").getMatch(0);
        if (filesize == null) {
            filesize = new Regex(BRBEFORE, "<small>\\((.*?)\\)</small>").getMatch(0);
            if (filesize == null) {
                filesize = new Regex(BRBEFORE, "</font>[ ]+\\((.*?)\\)(.*?)</font>").getMatch(0);
            }
        }
        if (filename == null || filename.equals("")) {
            if (BRBEFORE.contains("You have reached the download-limit")) {
                logger.warning("Waittime detected, please reconnect to make the linkchecker work!");
                return AvailableStatus.UNCHECKABLE;
            }
            logger.warning("The filename equals null, throwing \"plugin defect\" now...");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = filename.replaceAll("(</b>|<b>|\\.html)", "");
        link.setFinalFileName(filename.trim());
        if (filesize != null && !filesize.equals("")) {
            logger.info("Filesize found, filesize = " + filesize);
            link.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    private void waitTime(long timeBefore, DownloadLink downloadLink) throws PluginException {
        int passedTime = (int) ((System.currentTimeMillis() - timeBefore) / 1000) - 1;
        // Ticket Time
        String ttt = new Regex(BRBEFORE, "countdown\">.*?(\\d+).*?</span>").getMatch(0);
        if (ttt == null) ttt = new Regex(BRBEFORE, "id=\"countdown_str\".*?<span id=\".*?\">.*?(\\d+).*?</span").getMatch(0);
        if (ttt != null) {
            int tt = Integer.parseInt(ttt);
            tt -= passedTime;
            logger.info("Waittime detected, waiting " + ttt + " - " + passedTime + " seconds from now on...");
            if (tt > 0) sleep(tt * 1001l, downloadLink);
        }
    }

}