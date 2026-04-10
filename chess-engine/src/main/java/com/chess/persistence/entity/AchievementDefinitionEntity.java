package com.chess.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "achievement_definitions")
public class AchievementDefinitionEntity {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 10)
    private String icon;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String description;

    private Integer target;

    public String getId()          { return id; }
    public String getIcon()        { return icon; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public Integer getTarget()     { return target; }
}
