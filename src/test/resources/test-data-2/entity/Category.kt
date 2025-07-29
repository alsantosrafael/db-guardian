package com.example.entity

import jakarta.persistence.*

@Entity
@Table(name = "categories", indexes = [
    Index(name = "idx_category_name", columnList = "name", unique = true),
    Index(name = "idx_category_parent", columnList = "parent_id")
])
data class Category(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "name", nullable = false, unique = true, length = 100)
    val name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    val parent: Category?,

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    val children: List<Category> = emptyList(),

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    val products: List<Product> = emptyList()
)