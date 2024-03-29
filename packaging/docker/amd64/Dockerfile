# Copyright (c) 2021-2022, Jan Cajthaml <jan.cajthaml@gmail.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# ---------------------------------------------------------------------------- #

FROM --platform=linux/amd64 amd64/postgres:14

ENV TERM=xterm

ENV POSTGRES_DB=openbank

ENV POSTGRES_HOST_AUTH_METHOD=trust

COPY packaging/bin/* /opt/artifacts/

USER root

RUN apt-get update

RUN find /opt/artifacts -name postgres_*_amd64.deb | xargs -I{} -tx apt-get install -y -f {}

RUN cp /etc/postgres/conf.d/init.sql /docker-entrypoint-initdb.d/init.sql

RUN chmod a+r /docker-entrypoint-initdb.d/*

USER postgres

CMD ["postgres", "-c", "client_min_messages=DEBUG1", "-c", "log_min_messages=DEBUG1"]
