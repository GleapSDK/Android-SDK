package io.gleap;

public class OpenPushAction {
    private String type;
    private String id;

    // Constructor
    public OpenPushAction(String type, String id) {
        this.type = type;
        this.id = id;
    }

    // Getter for type
    public String getType() {
        return type;
    }

    // Setter for type
    public void setType(String type) {
        this.type = type;
    }

    // Getter for id
    public String getId() {
        return id;
    }

    // Setter for id
    public void setId(String id) {
        this.id = id;
    }

    // Optional: Override toString() method for easy printing
    @Override
    public String toString() {
        return "OpenPushAction{" +
                "type='" + type + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}