package io.gleap;

class GleapAction {
    private String actionType;
    private String outbound;

    public GleapAction(String actionType, String outbound) {
        this.actionType = actionType;
        this.outbound = outbound;
    }

    public String getActionType() {
        return actionType;
    }

    public String getOutbound() {
        return outbound;
    }
}
