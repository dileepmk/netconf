package org.opendaylight.netconf.topology.singleton.messages.utils;


public class NetworkElement {
    private String id;
    private String name;
    private int severity;
    private int alarmCount;
    private NetworkElementSeverity severityLevel;

    public String getName() {
        return name;
    }

    public int getAlarmCount() {
        return alarmCount;
    }

    public void setAlarmCount(int alarmCount) {
        this.alarmCount = alarmCount;
    }

    @Override
    public String toString() {
        return "NetworkElement{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", severity=" + severity +
                ", alarmCount=" + alarmCount +
                ", severityLevel=" + severityLevel +
                '}';
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public NetworkElementSeverity getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(NetworkElementSeverity severityLevel) {
        this.severityLevel = severityLevel;
    }

}
