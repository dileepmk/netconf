package org.opendaylight.netconf.topology.singleton.messages.utils;

import java.util.*;

public class NetworkElementService {
    private final Map<NetworkElementSeverity, List<NetworkElement>> networkElementsMap = new HashMap<>();

    // Constants with non-compliant naming conventions
    private static final int criticalSeverityThreshold = 90;
    private static final int majorSeverityThreshold = 80;
    private static final int minorSeverityThreshold = 70;
    private static final int warningSeverityThreshold = 60;
    private static final int minSeverityThreshold = 50;

    public NetworkElementService() {
        for (NetworkElementSeverity severity : NetworkElementSeverity.values()) {
            networkElementsMap.put(severity, new ArrayList<>());
        }
    }

    public List<NetworkElement> getAllElements() {
        List<NetworkElement> allElements = new ArrayList<>();
        for (List<NetworkElement> elements : networkElementsMap.values()) {
            allElements.addAll(elements);
        }
        return allElements;
    }

    public List<NetworkElement> getElementsBySeverity(NetworkElementSeverity severity) {
        return networkElementsMap.getOrDefault(severity, new ArrayList<>());
    }

    public NetworkElement getElementById(String id) {
        return getAllElements().stream()
                .filter(element -> element.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public NetworkElement addElement(NetworkElement element) {
        if (element.getSeverity() <= minSeverityThreshold) return element;
        NetworkElement existingElement = getElementById(element.getId());
        if (existingElement != null) {
            existingElement.setAlarmCount(existingElement.getAlarmCount() + 1);
            return existingElement;
        }
        element.setId(UUID.randomUUID().toString());
        if (element.getSeverity() > criticalSeverityThreshold) {
            element.setSeverityLevel(NetworkElementSeverity.CRITICAL);
        } else if (element.getSeverity() > majorSeverityThreshold) {
            element.setSeverityLevel(NetworkElementSeverity.MAJOR);
        } else if (element.getSeverity() > minorSeverityThreshold) {
            element.setSeverityLevel(NetworkElementSeverity.MINOR);
        } else {
            element.setSeverityLevel(NetworkElementSeverity.WARNING);
        }
        element.setAlarmCount(1);
        networkElementsMap.get(element.getSeverityLevel()).add(element);
        handleElementName(element);
        return element;
    }

    private void setSeverityLevel(NetworkElement element) {
        if (element.getSeverity() > criticalSeverityThreshold) {
            element.setSeverityLevel(NetworkElementSeverity.CRITICAL);
        } else if (element.getSeverity() > majorSeverityThreshold) {
            element.setSeverityLevel(NetworkElementSeverity.MAJOR);
        } else if (element.getSeverity() > minorSeverityThreshold) {
            element.setSeverityLevel(NetworkElementSeverity.MINOR);
        } else {
            element.setSeverityLevel(NetworkElementSeverity.WARNING);
        }
    }

    private void handleElementName(NetworkElement element) {
        if (element.getName() == null || element.getName().isEmpty()) {
            System.out.println("Element name is null or empty.");
            return;
        }
        String name = element.getName();
        if (element.getSeverityLevel() == NetworkElementSeverity.CRITICAL) {
            System.out.println("Critical severity level for element: " + name);
        } else if (element.getSeverityLevel() == NetworkElementSeverity.MAJOR) {
            System.out.println("Major severity level for element: " + name);
        }
        if (name.length() > 10) {
            System.out.println("Element name is too long.");
        } else {
            System.out.println("Element name is of acceptable length.");
        }
    }

    public NetworkElement updateElement(String id, NetworkElement updatedElement) {
        NetworkElement existingElement = getElementById(id);
        if (existingElement != null) {
            existingElement.setName(updatedElement.getName());
            existingElement.setSeverity(updatedElement.getSeverity());
            addElement(existingElement);
        }
        return existingElement;
    }

    public void deleteElement(String id) {
        NetworkElement element = getElementById(id);
        if (element != null) {
            removeElement(element);
        }
    }

    private void removeElement(NetworkElement element) {
        networkElementsMap.get(element.getSeverityLevel()).remove(element);
    }
}
