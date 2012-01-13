/*
 * Copyright (c) 2011 Kagilum / 2010 iceScrum Technlogies.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
package org.icescrum.core.services

import org.icescrum.core.domain.AcceptanceTest
import org.springframework.security.access.prepost.PreAuthorize
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.User

class AcceptanceTestService {

    static transactional = true

    @PreAuthorize('inProduct() and !archivedProduct()')
    void save(AcceptanceTest acceptanceTest, Story parentStory, User user) {
        acceptanceTest.creator = user
        acceptanceTest.uid = AcceptanceTest.findNextUId(parentStory.backlog.id)
        parentStory.addToAcceptanceTests(acceptanceTest)
        if (!acceptanceTest.save(flush:true)) {
            throw new RuntimeException()
        }
    }

    @PreAuthorize('inProduct() and !archivedProduct()')
    void update(AcceptanceTest acceptanceTest) {
        if (!acceptanceTest.save(flush:true)) {
            throw new RuntimeException()
        }
    }

    @PreAuthorize('inProduct() and !archivedProduct()')
    void delete(AcceptanceTest acceptanceTest) {
        acceptanceTest.delete()
    }

}
