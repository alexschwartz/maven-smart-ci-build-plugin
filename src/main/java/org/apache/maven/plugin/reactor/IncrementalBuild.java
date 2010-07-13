package org.apache.maven.plugin.reactor;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmRevision;
import org.apache.maven.scm.command.diff.DiffScmResult;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.repository.ScmRepository;

/**
 * Goal to build all projects that had been changed in SVN since the last successful build. 
 * To be used in CI system.
 * 
 * Is currently limited to build running in a SVN working copy.
 * 
 * @author <a href="mailto:dev@schwartzonline.de">Alex Schwartz</a>
 * @goal incremental-build
 * @aggregator
 * @phase process-sources
 */
public class IncrementalBuild
    extends MakeScmChanges
{
    /**
     * The SCM connection/provider info.  Should be specified in your POM.
     * @parameter expression="${make.scmConnection}" default-value="${project.scm.connection}"
     * @required
     */
    String scmConnection;

    /**
     * Ignore files in the "unknown" status (created but not added to source control)
     * 
     * @parameter expression="${make.ignoreUnknown}" default-value=true
     */
    private boolean ignoreUnknown = true;
    
    /**
     * The file to store the revision of the last successful build
     * 
     * @parameter default-value="${project.build.directory}/last-successful-build-revision.txt"
     */
    private File lastSuccessfulBuildRevisionFile;
    
    /**
     * The version current revision number.
     *
     * @parameter expression="${lastSuccessfulBuildScmVersion}"
     */
    private String lastSuccessfulBuildScmVersion;

    /**
     * The version current revision number.
     *
     * @parameter expression="${currentScmVersion}"
     */
    private String currentScmVersion;

     /**
     * @component
     */
    ScmManager scmManager;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( collectedProjects.size() == 0 )
        {
            throw new NonReactorException();
        }
        if ( scmConnection == null )
        {
            throw new MojoFailureException("No SCM connection specified.  You must specify an SCM connection by adding a <connection> element to your <scm> element in your POM");
        }
        DiffScmResult result = null;
        
        
        ScmRevision startRevision = new ScmRevision( this.lastSuccessfulBuildScmVersion );
        boolean hasLastSuccessfulBuildRevisionFile = lastSuccessfulBuildRevisionFile != null && lastSuccessfulBuildRevisionFile.exists();
        if ( this.lastSuccessfulBuildScmVersion != null && hasLastSuccessfulBuildRevisionFile) 
        {
        	throw new MojoFailureException( "Please specify either lastSuccessfulBuildRevisionFile or lastSuccessfulBuildScmVersion" );
        } 
        if ( hasLastSuccessfulBuildRevisionFile ) 
        {
            startRevision = new ScmRevision( 
                readFirstLine( lastSuccessfulBuildRevisionFile ) );
        }
        
        ScmRevision endRevision = new ScmRevision( this.currentScmVersion );

        getLog().info( "startRevision = " + startRevision);
        getLog().info( "endRevision   = " + endRevision);

        try
        {
            ScmRepository repository = scmManager.makeScmRepository( scmConnection ); 
            result = scmManager.diff( repository, new ScmFileSet( baseDir ), startRevision, endRevision );            
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Couldn't configure SCM repository: " + e.getLocalizedMessage(), e );
        }

        List changedFiles = result.getChangedFiles();
        
        executeMakeForChangedModules( changedFiles, this.ignoreUnknown );
        
        try
        {
            PrintWriter out = new PrintWriter(
            		new BufferedWriter( new FileWriter( lastSuccessfulBuildRevisionFile) ));
            out.println( currentScmVersion );
            out.close();
        }
        catch ( Exception e )
        {
            throw new MojoFailureException( "Failed to write file '"
                    + lastSuccessfulBuildRevisionFile + "'. Reason: ", e );
        }
        
    }

	private String readFirstLine(File file) 
	{
		String firstLine = null;
		try 
		{
			FileInputStream fstream = new FileInputStream( file );
		    // Get the object of DataInputStream
			DataInputStream in = new DataInputStream( fstream );
		    BufferedReader br = new BufferedReader( new InputStreamReader(in) );
		    
		    firstLine = br.readLine().trim();
		    in.close();
		} 
		catch ( Exception e)
		{
		    
		}
		return firstLine;
	}

}
