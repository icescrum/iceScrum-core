/*
 * Copyright (c) 2015 iceScrum Technologies.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 */


package org.icescrum.core.domain

import org.hibernate.ObjectNotFoundException
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.domain.security.UserAuthority
import org.icescrum.core.domain.security.UserToken
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable
import org.springframework.security.core.context.SecurityContextHolder as SCH

class User implements Serializable, Attachmentable {

    static final long serialVersionUID = 813639032272976126L

    String lastName = ""
    String firstName = ""
    String username = ""
    String password = ""
    String email

    Date dateCreated
    Date lastUpdated
    Date lastLogin

    UserPreferences preferences
    String uid


    boolean enabled = true
    boolean accountExternal = false
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired

    static transients = ['locale', 'admin']

    static hasMany = [
            teams : Team,
            tokens: UserToken
    ]

    static belongsTo = [Team]

    static mapping = {
        cache true
        table 'is_user'
        password column: 'passwd'
        username index: 'username_index'
        teams cache: true
        tokens cascade: 'all-delete-orphan', batchSize: 10
    }

    static constraints = {
        email(blank: false, unique: true, validator: { newEmail, user -> ApplicationSupport.isValidEmailAddress(newEmail) ?: 'invalid' }, shared: 'keyMaxSize')
        username(blank: false, unique: true, shared: 'keyMaxSize')
        password(blank: false)
        lastName(blank: false)
        firstName(blank: false)
        uid(blank: false)
        lastLogin(nullable: true)
    }

    static findUsersLike(term, excludeCurrentUser, showDisabled, params) {
        executeQuery("""SELECT DISTINCT u
                        FROM org.icescrum.core.domain.User AS u
                        WHERE ${showDisabled == false ? 'u.enabled = true AND ' : ''} ${excludeCurrentUser ? 'u.id != ' + SCH.context.authentication.principal?.id + ' AND ' : ''}
                        ( lower(u.email) LIKE lower(:term)
                        OR lower(u.username) LIKE lower(:term)
                        OR lower(u.firstName) LIKE lower(:term)
                        OR lower(u.lastName) LIKE lower(:term)
                        OR lower(concat(u.firstName,' ', u.lastName)) LIKE lower(:term)
                        OR lower(concat(u.lastName,' ', u.firstName)) LIKE lower(:term))
                        ORDER BY u.username ASC""", [term: "%$term%"], params ?: [:])
    }

    static countUsersLike(excludeCurrentUser, term, params) {
        executeQuery("""SELECT COUNT(DISTINCT u)
                        FROM org.icescrum.core.domain.User AS u
                        WHERE ${excludeCurrentUser ? 'u.id != ' + SCH.context.authentication.principal?.id + ' AND ' : ''}
                        ( lower(u.email) LIKE lower(:term)
                        OR lower(u.username) LIKE lower(:term)
                        OR lower(u.firstName) LIKE lower(:term)
                        OR lower(u.lastName) LIKE lower(:term)
                        OR lower(concat(u.firstName,' ', u.lastName)) LIKE lower(:term)
                        OR lower(concat(u.lastName,' ', u.firstName)) LIKE lower(:term))""", [term: "%$term%"], params ?: [:])[0]
    }

    static findUsersLikeAndEnabled(excludeCurrentUser, term, enabled, params) {
        executeQuery("""SELECT DISTINCT u
                        FROM org.icescrum.core.domain.User AS u
                        WHERE ${excludeCurrentUser ? 'u.id != ' + SCH.context.authentication.principal?.id + ' AND ' : ''}
                        ( lower(u.email) LIKE lower(:term)
                        OR lower(u.username) LIKE lower(:term)
                        OR lower(u.firstName) LIKE lower(:term)
                        OR lower(u.lastName) LIKE lower(:term)
                        OR lower(concat(u.firstName,' ', u.lastName)) LIKE lower(:term)
                        OR lower(concat(u.lastName,' ', u.firstName)) LIKE lower(:term))
                        AND enabled = :enabled
                        ORDER BY u.username ASC""", [term: "%$term%", enabled: enabled], params ?: [:])
    }

    static countUsersLikeAndEnabled(excludeCurrentUser, term, enabled, params) {
        executeQuery("""SELECT COUNT(DISTINCT u)
                        FROM org.icescrum.core.domain.User AS u
                        WHERE ${excludeCurrentUser ? 'u.id != ' + SCH.context.authentication.principal?.id + ' AND ' : ''}
                        ( lower(u.email) LIKE lower(:term)
                        OR lower(u.username) LIKE lower(:term)
                        OR lower(u.firstName) LIKE lower(:term)
                        OR lower(u.lastName) LIKE lower(:term)
                        OR lower(concat(u.firstName,' ', u.lastName)) LIKE lower(:term)
                        OR lower(concat(u.lastName,' ', u.firstName)) LIKE lower(:term))
                        AND enabled = :enabled """, [term: "%$term%", enabled: enabled], params ?: [:])[0]
    }

    static Locale getLocale(Long userId) {
        String language = executeQuery("""SELECT p.language
                                          FROM UserPreferences p
                                          WHERE p.user.id = :userId""", [userId: userId], [cache: true])[0]
        return getLocaleFromLanguage(language)
    }

    static Locale getLocaleFromLanguage(String language) {
        new Locale(*language.split('_', 3))
    }

    static User withUser(long id) {
        User user = get(id)
        if (!user) {
            throw new ObjectNotFoundException(id, 'User')
        }
        return user
    }

    static List<User> withUsers(def params, def id = 'id') {
        def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
        List<User> users = ids ? getAll(ids) : null
        if (!users) {
            throw new ObjectNotFoundException(ids, 'User')
        }
        return users
    }

    Set<Authority> getAuthorities() {
        UserAuthority.findAllByUser(this).collect { it.authority } as Set
    }

    boolean equals(obj) {
        if (this.is(obj)) {
            return true
        }
        if (obj == null) {
            return false
        }
        if (!getClass().isAssignableFrom(obj.getClass())) {
            return false
        }
        User other = (User) obj
        if (username == null) {
            if (other.username != null) {
                return false
            }
        } else if (!username.equals(other.username)) {
            return false
        }
        if (email == null) {
            if (other.email != null) {
                return false
            }
        } else if (!email.equals(other.email)) {
            return false
        }
        return true
    }

    int hashCode() {
        return username.hashCode()
    }

    def beforeValidate() {
        //Create uid before first save object
        this.email = this.email?.trim()
        if (!this.id && !this.uid) {
            this.uid = (this.username + this.email).encodeAsMD5()
        }
    }

    Locale getLocale() {
        getLocaleFromLanguage(preferences.language)
    }

    boolean getAdmin() {
        executeQuery("""SELECT COUNT(*)
                        FROM UserAuthority ua
                        WHERE ua.user.id = :userId
                        AND ua.authority.authority = :adminAuthority""", [userId: this.id, adminAuthority: Authority.ROLE_ADMIN], [cache: true])[0] > 0
    }

    def xml(builder) {
        builder.user(uid: this.uid) {
            builder.id(this.id)
            builder.email(this.email)
            builder.enabled(this.enabled)
            builder.username(this.username)
            builder.password(this.password)
            builder.lastLogin(this.lastLogin)
            builder.dateCreated(this.dateCreated)
            builder.lastUpdated(this.lastUpdated)
            builder.accountLocked(this.accountLocked)
            builder.accountExpired(this.accountExpired)
            builder.passwordExpired(this.passwordExpired)
            builder.accountExternal(this.accountExternal)
            builder.lastName { builder.mkp.yieldUnescaped("<![CDATA[${this.lastName}]]>") }
            builder.firstName { builder.mkp.yieldUnescaped("<![CDATA[${this.firstName}]]>") }

            preferences.xml(builder)

            builder.teams() {
                this.teams.each { _team ->
                    team(uid: _team.uid)
                }
            }
            exportDomainsPlugins(builder)
        }
    }
}
