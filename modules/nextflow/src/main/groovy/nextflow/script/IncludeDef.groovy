/*
 * Copyright 2020, Seqera Labs
 * Copyright 2013-2019, Centre for Genomic Regulation (CRG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.script

import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.Memoized
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.NF
import nextflow.Session
import nextflow.exception.IllegalModulePath
/**
 * Implements a script inclusion
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@EqualsAndHashCode
class IncludeDef {

    @Canonical
    static class Module {
        String name
        String alias
    }

    @PackageScope path
    @PackageScope List<Module> modules
    @PackageScope Map params
    @PackageScope Map addedParams
    private Session session

    @Deprecated
    IncludeDef( String module ) {
        final msg = "Anonymous module inclusion is deprecated -- Replace `include '${module}'` with `include { MODULE_NAME } from '${module}'`"
        if( NF.isDsl2Final() )
            throw new DeprecationException(msg)
        log.warn msg
        this.path = module
        this.modules = new ArrayList<>(1)
        this.modules << new Module(null,null)
    }

    IncludeDef(TokenVar token, String alias=null) {
        def component = token.name; if(alias) component += " as $alias"
        def msg = "Unwrapped module inclusion is deprecated -- Replace `include $component from './MODULE/PATH'` with `include { $component } from './MODULE/PATH'`"
        if( NF.isDsl2Final() )
            throw new DeprecationException(msg)
        log.warn msg

        this.modules = new ArrayList<>(1)
        this.modules << new Module(token.name, alias)
    }

    protected IncludeDef(List<Module> modules) {
        this.modules = new ArrayList<>(modules)
    }

    /** only for testing purpose -- do not use */
    protected IncludeDef() { }

    IncludeDef from(Object path) {
        this.path = path
        return this
    }

    IncludeDef params(Map args) {
        this.params = args != null ? new HashMap(args) : null
        return this
    }

    IncludeDef addParams(Map args) {
        this.addedParams = args
        return this
    }

    IncludeDef setSession(Session session) {
        this.session = session
        return this
    }

    /*
     * Note: this method invocation is injected during the Nextflow AST manipulation.
     * Do not use it explicitly.
     *
     * @param ownerParams The params in the owner context
     */
    void load0(ScriptBinding.ParamsMap ownerParams) {
        checkValidPath(path)
        // -- load the module
        final validPath = path as Path
        final moduleScript = loadModule0(validPath, resolveParams(ownerParams), session)
        // -- add it to the inclusions
        for( Module module : modules ) {
            meta.addModule(moduleScript, module.name, module.alias)
        }
    }

    private Map resolveParams(ScriptBinding.ParamsMap ownerParams) {
        if( params!=null && addedParams!=null )
            throw new IllegalArgumentException("Include 'params' and 'addParams' option conflict -- check module: $path")
        if( params!=null )
            return params

        addedParams ? ownerParams.copyWith(addedParams) : ownerParams
    }

    @PackageScope
    static ScriptMeta getMeta() { ScriptMeta.current() }

    @PackageScope
    static Path getOwnerPath() { getMeta().getScriptPath() }

    @PackageScope
    @Memoized
    static BaseScript loadModule0(Path path, Map params, Session session) {
        final moduleFile = realModulePath(path, session)

        final binding = new ScriptBinding() .setParams(params)

        // the execution of a library file has as side effect the registration of declared processes
        new ScriptParser(session)
                .setModule(true)
                .setBinding(binding)
                .runScript(moduleFile)
                .getScript()
    }

    @PackageScope
    static Path resolveModulePath(Path include, Session session) {
        assert include

        final result = include as Path
        if( result.isAbsolute() ) {
            if( result.scheme == 'file' ) return result
            throw new IllegalModulePath("Cannot resolve module path: ${result.toUriString()}")
        }

        final str = result.toString()
        if( str.startsWith('./') || str.startsWith('../') ) {
            return getOwnerPath().resolveSibling(include.toString())
        } else {
            return Paths.get(session.workflowMetadata.projectDir.toString(), str)
        }
    }

    @PackageScope
    static Path realModulePath(Path include, Session session) {
        def module = resolveModulePath(include, session)

        // check if exists a file with `.nf` extension
        if( !module.name.endsWith('.nf') ) {
            def extendedName = module.resolveSibling( "${module.name}.nf" )
            if( extendedName.exists() )
                return extendedName
        }

        // check the file exists
        if( module.exists() )
            return module

        throw new NoSuchFileException("Can't find a matching module file for include: $include")
    }

    @PackageScope
    void checkValidPath(path) {
        if( !path )
            throw new IllegalModulePath("Missing module path attribute")

        if( path instanceof Path && path.scheme != 'file' )
            throw new IllegalModulePath("Remote modules are not allowed -- Offending module: ${path.toUriString()}")

    }


}
