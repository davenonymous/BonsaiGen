#!/usr/bin/perl

use strict;
use warnings;

use Mojo::UserAgent;
use Data::Dumper;

my $gameVersionId = 11779; # Minecraft 1.21.1
my $gameFlavorId = 6;      # Neoforge
my $baseUrl = 'https://www.curseforge.com/api/v1/mods/%s/files?pageIndex=0&pageSize=1&sort=dateCreated&sortDescending=true&removeAlphas=false&gameVersionId=' . $gameVersionId . '&gameFlavorId=' . $gameFlavorId;
my $configFile = 'mods.conf';

my $ua = Mojo::UserAgent->new->max_redirects(5);
my @updatedLines = ();

open(my $fh, '<', $configFile) or die "Could not open file '$configFile' $!";
while(my $line = <$fh>) {
    chomp $line;
    if($line =~ /^\s*#/ || $line =~ /^\s*$/) {
        push @updatedLines, $line;
        next;
    }
    my ($projectSlug, $projectId, $fileId) = split(/\s+/, $line);
    my $url = sprintf($baseUrl, $projectId);

    printf("Checking %s\n", $projectSlug);
    my $response = $ua->get($url)->result;
    if ($response->is_error) {
        warn "Error fetching $url: " . $response->message;
        push @updatedLines, $line;
        next;
    }
    my $json = $response->json;
    if (!defined $json || ref($json) ne 'HASH') {
        warn "Invalid JSON response from $url";
        push @updatedLines, $line;
        next;
    }
    my $files = $json->{data};
    if (!defined $files || ref($files) ne 'ARRAY' || @$files == 0) {
        warn "No files found for $url";
        push @updatedLines, $line;
        next;
    }

    my $latestFile = $files->[0];
    my $latestFileId = $latestFile->{id};
    my $latestFileName = $latestFile->{fileName};
    my $latestFileDate = $latestFile->{dateCreated};
    my $newLine = sprintf("%-40s %-12s %-12s # %-25s | %s", $projectSlug, $projectId, $latestFileId, $latestFileDate, $latestFileName);
    push @updatedLines, $newLine;
}
close($fh);

print join("\n", @updatedLines) . "\n";
