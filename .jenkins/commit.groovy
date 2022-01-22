def DOCKER_IMAGE

def dockerOptions() {
    String options = "--pull "
    options += "--label 'org.opencontainers.image.source=${env.GIT_URL}#${env.GIT_BRANCH}' "
    options += "--label 'org.opencontainers.image.created=${env.RFC3339_DATETIME}' "
    options += "--label 'org.opencontainers.image.revision=${env.GIT_COMMIT}' "
    options += "--label 'org.opencontainers.image.licenses=${env.LICENSE}' "
    options += "--label 'org.opencontainers.image.authors=${env.PROJECT_AUTHOR}' "
    options += "--label 'org.opencontainers.image.title=${env.PROJECT_NAME}' "
    options += "--label 'org.opencontainers.image.description=${env.PROJECT_DESCRIPTION}' "
    options += "-f ./packaging/docker/amd64/Dockerfile "
    options += "."
    return options
}

def getVersion() {
    String[] versions = (sh(
        script: 'git fetch --tags --force 2> /dev/null; tags=\$(git tag --sort=-v:refname | head -1) && ([ -z \${tags} ] && echo v0.0.0 || echo \${tags})',
        returnStdout: true
    ).trim() - 'v').split('\\.')
    String major = versions[0]
    String minor = versions[1]
    Integer patch = Integer.parseInt(versions[2], 10)
    String version = "${major}.${minor}.${patch + 1}"
    return version
}

def artifactory = Artifactory.server "artifactory"

pipeline {

    agent {
        label 'docker'
    }

    options {
        skipDefaultCheckout(true)
        ansiColor('xterm')
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        disableConcurrentBuilds()
        disableResume()
        timeout(time: 10, unit: 'MINUTES')
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                script {
                    currentBuild.displayName = "#${currentBuild.number} - ? (?)"
                }
                deleteDir()
                checkout(scm)
            }
        }

        stage('Setup') {
            steps {
                script {
                    env.RFC3339_DATETIME = sh(
                        script: 'date --rfc-3339=ns',
                        returnStdout: true
                    ).trim()
                    env.GIT_COMMIT = sh(
                        script: 'git log -1 --format="%H"',
                        returnStdout: true
                    ).trim()
                    env.GIT_URL = sh(
                        script: 'git ls-remote --get-url',
                        returnStdout: true
                    ).trim()
                    env.GIT_BRANCH = sh(
                        script: 'git name-rev --name-only HEAD',
                        returnStdout: true
                    ).trim() - 'remotes/origin/'
                     env.ARCH = sh(
                        script: 'dpkg --print-architecture',
                        returnStdout: true
                    ).trim()

                    env.VERSION = getVersion()
                    env.LICENSE = "Apache-2.0"
                    env.PROJECT_NAME = "openbank postgres"
                    env.PROJECT_DESCRIPTION = "OpenBanking Postgres schema"
                    env.PROJECT_AUTHOR = "${env.CHANGE_AUTHOR_DISPLAY_NAME} <${env.CHANGE_AUTHOR_EMAIL}>"

                    currentBuild.displayName = "#${currentBuild.number} - ${env.GIT_BRANCH} (${env.VERSION})"
                }
            }
        }

        stage('Package Debian') {
            agent {
                docker {
                    image 'jancajthaml/debian-packager:latest'
                    args "--entrypoint=''"
                    reuseNode true
                }
            }
            steps {
                script {
                    sh """
                        ${env.WORKSPACE}/dev/lifecycle/debian \
                        --version ${env.VERSION} \
                        --arch ${env.ARCH} \
                        --pkg postgres \
                        --source ${env.WORKSPACE}/packaging
                    """
                }
            }
        }

        stage('Package Docker') {
            steps {
                script {
                    DOCKER_IMAGE = docker.build("${env.ARTIFACTORY_DOCKER_REGISTRY}/docker-local/openbank/postgres:amd64-${env.VERSION}.jenkins", dockerOptions())
                }
            }
        }

        stage('Publish') {
            steps {
                script {
                    docker.withRegistry("http://${env.ARTIFACTORY_DOCKER_REGISTRY}", 'jenkins-artifactory') {
                        DOCKER_IMAGE.push()
                    }
                    artifactory.upload spec: """
                    {
                        "files": [
                            {
                                "pattern": "${env.WORKSPACE}/init.sql",
                                "target": "generic-local/openbank/postgres/${env.VERSION}/init.sql",
                                "recursive": "false"
                            },
                            {
                                "pattern": "${env.WORKSPACE}/packaging/bin/postgres_(*)_(*).deb",
                                "target": "generic-local/openbank/postgres/{1}/linux/{2}/postgres.deb",
                                "recursive": "false"
                            }
                        ]
                    }
                    """
                }
            }
        }
    }

    post {
        always {
            script {
                if (DOCKER_IMAGE != null) {
                    sh "docker rmi -f ${DOCKER_IMAGE.id} || :"
                }
            }
            cleanWs()
        }
    }
}
