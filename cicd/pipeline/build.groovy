properties([
    pipelineTriggers([
        pollSCM('')
    ]),
    buildDiscarder(
        logRotator(
            daysToKeepStr: '20',
            artifactDaysToKeepStr: '60'
        )
    ),
    disableConcurrentBuilds()
])

timeout(45) {
    String repositoryName = "com.example.demo"
    String podName = repositoryName.take(50)
    String nodeLabel = "n" + env.BUILD_TAG.reverse().take(62).reverse().replaceAll('%2F', '-').replaceAll(/^\-/, '')    
    String buildNumber = env.BUILD_NUMBER 
	gitCommitHash = "unknown"
    gitCommitDate = "unknown"	    
	node
	{
	docker.image('singhdilraj1988/dk11-mvn-git-docker:v1.0').inside {
	try {
        stage("Clone repo") {
			checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/singhdilraj1988/com.example.demo.git']]])
            sh "ls -lart ./*"		
	        gitCommitHash = sh (script: "git reflog show origin/${env.BRANCH_NAME} --pretty=\'%h\' -n 1",returnStdout: true).trim()
            gitCommitDate = sh (script: "git reflog show origin/${env.BRANCH_NAME} --pretty=\'%gd\' --date=format:%Y%m%d%H%M -n 1",returnStdout: true).trim().replace("origin/${env.BRANCH_NAME}@{",'').replace("}",'')			
        } // end stage Clone repo
 	    stage("Run Code Checkstyle") {
                sh"""
                    mvn checkstyle:checkstyle
                """        	      
        } //end stage Run Code Checkstyle               
        stage("Build & Test Project") {                
                sh """
                     mvn install                        
                """                 
        } // end stage Build & Test Project
        stage("Build and Push Docker Image") {                                            
                   tag = this.gitCommitDate + "-" + this.gitCommitHash + "-" + env.BUILD_NUMBER
                   echo " docker tag is set : $tag"
                   docker.withRegistry("https://registry.hub.docker.com", "docker-hub-cred") {
                   def dockerfile = 'cicd/docker/Dockerfile'
                   def customImage = docker.build("singhdilraj1988/com.example.demo:$tag","-f ${dockerfile} .")
                   customImage.push()
                   sh "docker rmi singhdilraj1988/com.example.demo:$tag"
                }
            } //end stage                
    } //end try
    catch(IllegalStateException ex) {
        // This is thrown from cloneRepository step in case of intermediate commit build
        currentBuild.result = currentBuild.result ?: "NOT_BUILT"
        println ex.getMessage()
    }
    catch(Exception e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        println e.getMessage()
        throw e
    }
    finally {
        stage("Run post-build tasks") {                   
            
        } // end stage Run post-build tasks
    } // end finally      
	}
 }
} // end timeout
