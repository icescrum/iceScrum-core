package org.icescrum.core.domain

import grails.converters.JSON


class Backlog {

    String name
    String filter
    boolean shared

    static belongsTo = [
            product: Product,
            owner: User
    ]

    static mapping = {
        cache true
        name (blank:false)
        table 'icescrum2_backlogs'
    }

    static transients = [
            'count', 'stories'
    ]

    static constraints = {
        name(blank: false, maxSize: 200, unique: true)
    }

    def getCount(){
        return Story.search(product, JSON.parse(this.filter), false, true)
    }

    def getStories(){
        return Story.search(product, JSON.parse(this.filter), false)
    }

    static namedQueries = {
        findAllByProductAndSharedOrOwner { p, s, u ->
            product{
                eq 'id', p
            }
            or {
                eq 'shared', s
                owner {
                    eq 'id', u
                }
            }
        }
    }
}