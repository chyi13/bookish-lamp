package com.android.cy.wechatcrawler.services;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipboardManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Administrator on 2016/1/29.
 */
public class WechatMessageService extends AccessibilityService{
    private final static String TAG = "WechatCrawlerService";

    private final static int SERVICE_STATUS_NULL = -1;
    private final static int SERVICE_STATUS_GET_ACCOUNT_LIST = 1;
    private final static int SERVICE_STATUS_GET_ACCOUNT_DETAIL = 2;
    private final static int SERVICE_STATUS_GET_URL = 3;
    private final static int SERVICE_STATUS_GO_BACK = 4;
    private final static int SERVICE_STATUS_GO_BACK_TO_NULL = 5;
    private int serviceState = SERVICE_STATUS_NULL;

    private AccessibilityNodeInfo currentNewMsgAccountNode = null;
    private AccessibilityNodeInfo currentNewMsgMoreBtnNode = null;
    private AccessibilityNodeInfo currentNewMsgBackBtnNode = null;

    private String currentMsgContentTitle = "";

    private int currentMsgIndex = 0;
    
    private final static String NEW_MSG_FLAG = "unread message";
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (event.getSource() == null) return;
        Log.d(TAG, "onAccessibilityEvent");
        AccessibilityNodeInfo root = event.getSource();
        switch (serviceState) {
            case SERVICE_STATUS_NULL:
                Log.d(TAG, "onAccessibilityEvent:info=findNewMsgNode(0)");
                findNewMsgNode(root);
                break;
            case SERVICE_STATUS_GET_ACCOUNT_LIST:
                Log.d(TAG, "onAccessibilityEvent:info=getNewMsgNodeContent(1)");
                getNewMsgNodeContent(root);
                break;
            case SERVICE_STATUS_GET_ACCOUNT_DETAIL:
                Log.d(TAG, "onAccessibilityEvent:info=getNewMsgNodeContentDetail(2)");
                getNewMsgNodeContentDetail(root);
                break;
            case SERVICE_STATUS_GET_URL:
                Log.d(TAG, "onAccessibilityEvent:info=getNewMsgNodeContentDetailURL(3)");
                getNewMsgNodeContentDetailURL(root);
                break;
            case SERVICE_STATUS_GO_BACK:
                Log.d(TAG, "onAccessibilityEvent:info=goBackToNewMsgContent(4)");
                goBackToNewMsgContent(root);
                break;
            case SERVICE_STATUS_GO_BACK_TO_NULL:
                Log.d(TAG, "onAccessibilityEvent:info=goBackToAccountList(5)");
                goBackToAccountList(root);
                break;
            default:
                break;
        }
    }

    private AccessibilityNodeInfo getRootNode(AccessibilityNodeInfo nodeInfo) {
        AccessibilityNodeInfo root = nodeInfo;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return root;
    }
    // 0
    private void findNewMsgNode(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return;
        }

        AccessibilityNodeInfo listNodeInfo = getListViewNewMsg(nodeInfo);

        if (listNodeInfo == null || listNodeInfo.getClassName().equals("android.widget.ListView")) {
            currentNewMsgAccountNode = null;
            for (int i = 0; i<listNodeInfo.getChildCount(); i++) {
                if (checkAccountNewMsg(listNodeInfo.getChild(i))) {
                    if (listNodeInfo.getChild(i).performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        currentNewMsgAccountNode = listNodeInfo.getChild(i);
                        Log.d(TAG, "findNewMsgNode:info=clicked, des=" + currentNewMsgAccountNode.getContentDescription());
                        currentMsgIndex = 0;
                        serviceState = SERVICE_STATUS_GET_ACCOUNT_LIST;
                        return;
                    } else {
                        Log.d(TAG, "findNewMsgNode:info=not clickable");
                    }
                }
            }
        } else {
            Log.d(TAG, "findNewMsgNode:info=no new msg");
            return;
        }
    }

    private AccessibilityNodeInfo getListViewNewMsg(AccessibilityNodeInfo nodeInfo) {
        AccessibilityNodeInfo root = getRootNode(nodeInfo);
        try {
            if (root.getClassName().equals("android.widget.ListView")) {
                Log.d(TAG, "getListViewNewMsg:info=find android.widget.ListView");
                return root;
            }
            if (root.getClassName().equals("android.widget.FrameLayout")) {
                Log.d(TAG, "getListViewNewMsg:info=find android.widget.FrameLayout, find=" + root.getChild(0).getChild(1).getClassName()
                                + " childcount=" + root.getChild(0).getChild(1).getChildCount());
                return root.getChild(0).getChild(1);
            }
            return null;
        } catch (Exception e) {
            Log.d(TAG, "getListViewNewMsg:info=error");
            return null;
        }

    }

    private boolean checkAccountNewMsg(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null || nodeInfo.getContentDescription() == null)
            return false;
        String messageLine = nodeInfo.getContentDescription().toString();
        // check if contain new message
        return messageLine.contains(NEW_MSG_FLAG);
    }
    // 1
    private void getNewMsgNodeContent(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return;
        }
        // if from the content page, check if it is still on it
        if (checkIfWebView(getRootNode(nodeInfo))) {
            return;
        }

        // find msg ListView node
        AccessibilityNodeInfo listViewNode = getListViewContent(nodeInfo);

        if (listViewNode ==  null) {
            Log.d(TAG, "getNewNodeContent:info=Error not find listView");
            return;
        }
        // find go back button
        AccessibilityNodeInfo accountContentGoBackBtn = getAccountContentBackBtnNode(nodeInfo);
        if (accountContentGoBackBtn == null) {
            Log.d(TAG, "getNewMsgNodeContent:info=cannot find go back btn in account content page");
        }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // get RelativeLayout for each message
        Log.d(TAG, "getNewMsgNodeContent:info=find listView, nodeCount=" + listViewNode.getChildCount());
        ArrayList<AccessibilityNodeInfo> msgList = new ArrayList<AccessibilityNodeInfo>();
        for (int i = 0; i<listViewNode.getChildCount(); i++) {
            AccessibilityNodeInfo tMsg = listViewNode.getChild(i);
            if (tMsg != null && tMsg.getClassName().equals("android.widget.RelativeLayout")) {
                msgList.add(tMsg);
            }
        }
        // if no message shows
        if (msgList.isEmpty()) {
            return;
        }
        // get the latest msg
        AccessibilityNodeInfo latestRelativeNode = msgList.get(msgList.size()-1);
        AccessibilityNodeInfo latestMsgNode = null;
        // check if contains a time TextView
        if (latestRelativeNode.getChild(0).getClassName().equals("android.widget.TextView")) {
            latestMsgNode = latestRelativeNode.getChild(1);
        } else {
            latestMsgNode = latestRelativeNode.getChild(0);
        }
        // 1 msg 1 article
//        if (listViewNode.getChildCount() == 2) {
//            Log.d(TAG, "llllistViewNode:info=" +  listViewNode.getChildCount());
//            latestNode = listViewNode.getChild(1).getChild(1);
//        } else {
//            Log.d(TAG, "llllistViewNode:info=" +  listViewNode.getChildCount());
//            AccessibilityNodeInfo tempNode = listViewNode.getChild(listViewNode.getChildCount()-1);
//            if (tempNode.getChildCount() == 2) {
//                latestNode = tempNode.getChild(1).getChild(0);
//            } else {
//                latestNode = tempNode.getChild(0).getChild(0);
//            }
//        }
//        if (listViewNode.getChildCount() == 1) {     // 1 msg multiple articles
//            latestNode = listViewNode.getChild(0).getChild(1).getChild(0);
//        } else {
//            latestNode = listViewNode.getChild(listViewNode.getChildCount() - 1).getChild(1).getChild(0);
//        }
        int currentMsgCount = latestMsgNode.getChildCount();
        Log.d(TAG, "getNewNodeContent:info=null, latestNodeCount=" + currentMsgCount + " currentMsgIndex=" + currentMsgIndex);

        // if already traverse all sub-message, go back to previous page
        if (currentMsgIndex >= currentMsgCount) {
            Log.d(TAG, "accountContentGoBackBtn:info=Error not find lastest node");
            if (accountContentGoBackBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "accountContentGoBackBtn:info=go back to null");
                serviceState = SERVICE_STATUS_NULL;
            } else {
                Log.d(TAG, "accountContentGoBackBtn:info=not clickable");
            }
            return;
        }

        // click each message
        if (!latestMsgNode.getChild(currentMsgIndex).performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "getNewNodeContent:info=Error not clickable node");
            serviceState = SERVICE_STATUS_NULL;
            return;
        }
        // get message title
        Log.d(TAG, "search article name...");
        if (currentMsgIndex == 0 && currentMsgCount > 1) {
            currentMsgContentTitle = latestMsgNode.getChild(currentMsgIndex).getChild(0).getContentDescription().toString();
        } else {
            currentMsgContentTitle = latestMsgNode.getChild(currentMsgIndex).getChild(0).getText().toString();
        }
        Log.d(TAG, "getNewNodeContent:info=null, article name=" + currentMsgContentTitle );
        currentMsgIndex++;
        serviceState = SERVICE_STATUS_GET_ACCOUNT_DETAIL;
    }

    private AccessibilityNodeInfo getListViewContent(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return null;
        }
        AccessibilityNodeInfo root = getRootNode(nodeInfo);
        if (root == null
                || !(root.getClassName().equals("android.widget.FrameLayout")) ) {
            return null;
        }
        try {
            AccessibilityNodeInfo listViewNodeParent = root.getChild(1).getChild(0);
            AccessibilityNodeInfo listViewNode = null;
            for (int i = 0; i<listViewNodeParent.getChildCount(); i++) {
                if (listViewNodeParent.getChild(i).getClassName().equals("android.widget.ListView")) {
                    listViewNode = listViewNodeParent.getChild(i);
                    break;
                }
            }
            Log.d(TAG, "search list view,,,,,");
            if (listViewNode.getClassName().toString().equals("android.widget.ListView")) {
                Log.d(TAG, "getListViewContent:info=find list view node");
                return listViewNode;
            } else {
                Log.d(TAG, "findListView:info=error not list view node");
                return null;
            }
        } catch (Exception e) {
            Log.d(TAG, "findListView:info=error");
            return null;
        }
    }

    private AccessibilityNodeInfo getListViewNode(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return null;
        }
        if (nodeInfo.getClassName().equals("android.widget.ListView")) {
            return nodeInfo;
        }
        AccessibilityNodeInfo tempResult = null;
        for (int i = 0; i<nodeInfo.getChildCount(); i++) {
            if (nodeInfo.getChild(i).getClassName().equals("android.widget.ListView")) {
                tempResult = nodeInfo.getChild(i);
                break;
            }
        }
        return tempResult;
    }

    private AccessibilityNodeInfo getAccountContentBackBtnNode(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return null;
        }
        AccessibilityNodeInfo root = getRootNode(nodeInfo);
        try {
            // FrameLayout->FrameLayout(1)->LinearLayout(0)->LinearLayout(0)->ImageView(0)
            AccessibilityNodeInfo goBackBtn = root.getChild(1).getChild(0).getChild(0).getChild(0);
            if (goBackBtn.getContentDescription().equals("Back")) {
                return goBackBtn.getParent();
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // 2
    // get url and go back to previous page
    private void getNewMsgNodeContentDetail(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return;
        }
        AccessibilityNodeInfo root = getRootNode(nodeInfo);
        if (!checkIfWebView(root)) {
            Log.d(TAG, "getNewMsgNodeContentDetail:info=not webView");
            Log.d(TAG, "try to search url button");
            getNewMsgNodeContentDetailURL(root);
        }
        currentNewMsgMoreBtnNode = getNewMsgMoreBtnNode(root);
        currentNewMsgBackBtnNode = getNewMsgBackBtnNode(root);
        if (currentNewMsgBackBtnNode == null) {
            Log.d(TAG, "getNewMsgNodeContentDetail:info= not find back");
        }

        if (currentNewMsgMoreBtnNode !=null) {
            if (!currentNewMsgMoreBtnNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "getNewMsgNodeContentDetail:info=not clickable");
            } else {
                // Copy url
                Log.d(TAG, "getNewMsgNodeContentDetail:info=go to copy page");
                serviceState = SERVICE_STATUS_GET_URL;
            }
        }
    }

    private boolean checkIfWebView(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return false;
        }
        try {
            if (nodeInfo.getChild(0).getChild(2).getClassName().equals("android.widget.ProgressBar")) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.d(TAG, "checkIfWebView:Error");
            return false;
        }
    }

    private AccessibilityNodeInfo getNewMsgMoreBtnNode(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return null;
        }
        try {
            // FrameLayout->FrameLayout(0)->View(0)->TextView(1)
            AccessibilityNodeInfo btnNode = nodeInfo.getChild(0).getChild(0).getChild(1);
            if (btnNode.getContentDescription().equals("More")) {
                return btnNode;
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private AccessibilityNodeInfo getNewMsgBackBtnNode(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return null;
        }
        try {
            Log.d(TAG, "Search backbtn");
            // FrameLayout->FrameLayout(0)->View(0)->LinearLayout(0)->LinearLayout(0)->ImageView(0)
            AccessibilityNodeInfo backBtnNode = nodeInfo.getChild(0).getChild(0).getChild(0).getChild(0).getChild(0);
            Log.d(TAG, backBtnNode.getContentDescription().toString());
            if (backBtnNode.getContentDescription().equals("Back")) {
                return backBtnNode;
            } else {
                return null;
            }
        } catch(Exception e) {
            Log.d(TAG, "getNewMsgBackBtnNode:info=Error");
            return null;
        }
    }

    // 3
    private void getNewMsgNodeContentDetailURL(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return;
        }
        Log.d(TAG, "search url...");
        AccessibilityNodeInfo root = getRootNode(nodeInfo);
        AccessibilityNodeInfo copyURLBtnNode = getCopyURLBtnNode(root);
        if (copyURLBtnNode == null) {
            Log.d(TAG, "getNewMsgNodeContentDetailURL:info=not find url btn");
            serviceState = SERVICE_STATUS_GET_ACCOUNT_DETAIL;
            return;
        }
        try {
            copyURLBtnNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            ClipboardManager clipboardManager = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            String currentMsgContentURL = clipboardManager.getPrimaryClip().getItemAt(0).getText().toString();
            Log.d(TAG, "getNewMsgNodeContentDetailURL:info=null, clipboard=" + currentMsgContentURL);
            CrawlerMessage msg = new CrawlerMessage();
            msg.init(currentMsgContentTitle, currentMsgContentURL);
            CrawlerConnectService.getInstance().sendNewMessage(msg);
            serviceState = SERVICE_STATUS_GO_BACK;
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "getNewMsgNodeContentDetailURL:info=Error ");
        }
    }

    private AccessibilityNodeInfo getCopyURLBtnNode(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return null;
        }
        try {
            Log.d(TAG, "search url button...");
            // FrameLayout->ListView(0)->LinearLayout(3)
            AccessibilityNodeInfo urlBtn = nodeInfo.getChild(0).getChild(3);
            if (urlBtn != null && urlBtn.getClassName().equals("android.widget.LinearLayout")
                    && urlBtn.getParent().getClassName().equals("android.widget.ListView")
                    && urlBtn.getParent().getChildCount() == 9) {
                return urlBtn;
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "getCopyURLBtnNode:info=Error");
            return null;
        }
    }

    // 4
    private void goBackToNewMsgContent(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return;
        }
        try {
            AccessibilityNodeInfo root = getRootNode(nodeInfo);
            currentNewMsgBackBtnNode = getNewMsgBackBtnNode(root);
            if (currentNewMsgBackBtnNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "goBackToNewMsgContent:info=clickable");
                serviceState = SERVICE_STATUS_GET_ACCOUNT_LIST;
            } else {
                Log.d(TAG, "goBackToNewMsgContent:info=not clickable");
            }
        } catch (Exception e) {
            Log.d(TAG, "goBackToNewMsgContent:info=Error");
        }
    }

    // 5
    private AccessibilityNodeInfo goBackToAccountList(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return null;
        }
        AccessibilityNodeInfo root = getRootNode(nodeInfo);
        Log.d(TAG, "find back button(null)");
        traverseNode(root, 0);

        return null;
    }

    private void traverseNode(AccessibilityNodeInfo nodeInfo, int depth) {
        if (nodeInfo == null) {
            return;
        }
        String description = "";
        if (nodeInfo.getContentDescription() != null) {
            description = nodeInfo.getContentDescription().toString();
        }
        Log.d(TAG, "traverseNode:info=parent, classname="+nodeInfo.getClassName() + " depth=" + depth + " des=" + description);
        for (int i = 0; i<nodeInfo.getChildCount(); i++) {
            Log.d(TAG, "traverseNode:info=child, classname=" + nodeInfo.getChild(i).getClassName() + " depth=" + (depth+1));
        }
        for (int i = 0; i<nodeInfo.getChildCount(); i++) {
            traverseNode(nodeInfo.getChild(i), depth+1);
        }
    }

    @Override
    public void onInterrupt() {

    }
}
