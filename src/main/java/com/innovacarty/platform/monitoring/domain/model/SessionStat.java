package com.innovacarty.platform.monitoring.domain.model;

import jakarta.persistence.*;

/** Shopping sessions started per hour of the day. */
@Entity
@Table(name = "session_stats")
public class SessionStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "hour_label")
    private String hour;
    private int sessions;

    public SessionStat() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHour() {
        return hour;
    }

    public void setHour(String hour) {
        this.hour = hour;
    }

    public int getSessions() {
        return sessions;
    }

    public void setSessions(int sessions) {
        this.sessions = sessions;
    }
}
