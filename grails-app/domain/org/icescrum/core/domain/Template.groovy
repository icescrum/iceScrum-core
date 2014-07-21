package org.icescrum.core.domain

class Template {

    String name
    String itemClass
    String data
    Product product

    static constraints = {
        name(blank: false, unique: 'product')
    }

    static mapping = {
        data type: "text"
        table 'icescrum2_template'
    }
}