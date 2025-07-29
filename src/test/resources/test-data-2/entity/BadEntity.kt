package com.example.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "bad_entities")
data class BadEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "name")
    val name: String,

    // BAD: EAGER loading causes N+1 queries
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    val parent: BadEntity?,

    // BAD: EAGER loading of collections
    @OneToMany(mappedBy = "parent", fetch = FetchType.EAGER)
    val children: List<BadEntity> = emptyList(),

    // BAD: No cascade strategy defined for delete operations
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "bad_entity_tags",
        joinColumns = [JoinColumn(name = "entity_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    val tags: Set<Tag> = emptySet(),

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)