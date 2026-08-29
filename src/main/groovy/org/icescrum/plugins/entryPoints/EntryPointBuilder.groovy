/*
* Copyright (c) 2011 Vincent Barrier.
*
*
* entry-points is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License.
*
* entry-points is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.icescrum.plugins.entryPoints

class EntryPointBuilder {

    private final _data

    EntryPointBuilder(def data) {
        _data = data
    }

    void action(String value) {
        _data.action = value
    }

    void controller(String value) {
        _data.controller = value
    }

    void ref(List _ref) {
        _data.ref.addAll(_ref)
    }

    void ref(String[] _ref) {
        _data.ref.addAll(_ref.toList())
    }

    void ref(String _ref) {
        ref(_ref.split(',')*.trim())
    }

    void template(String _template) {
        _data.template = _template
    }

    void form(_form) {
        _data.form = _form
    }

    def missingMethod(String name, args) {
        throw new RuntimeException("Sorry - flavours are not yet supported by the builder!")
    }
}
